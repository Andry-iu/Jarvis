#!/usr/bin/env python3
"""One-way phone book mirror. Transport is the existing authenticated JarvisWake MCP plugin.

No phone tokens here. Feed tool results on stdin; CLI output contains only plans/counts,
except an explicit nonempty search. No unencrypted contact content is written to disk.
"""
import argparse
import base64
import contextlib
from difflib import SequenceMatcher
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import stat
import sys
import time
import uuid
import unicodedata
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = Path('/workspace/.jarvis-private/phonebook')
MAX_BYTES = 8 * 1024 * 1024
CHUNK = 256 * 1024
LEGACY_CHUNK = 6144
MAX_RESPONSE_BYTES = 4 * ((CHUNK + 2) // 3) + 8192
UUID = re.compile(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')

class BookError(Exception):
    pass

def require(condition, code='invalid_export'):
    if not condition:
        raise BookError(code)

def identifier(value):
    require(isinstance(value, str) and UUID.fullmatch(value), 'invalid_id')
    return value

def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf-8')

def exact(value, keys):
    require(isinstance(value, dict) and set(value) == set(keys.split()))

def integer(value, minimum, maximum):
    return type(value) is int and minimum <= value <= maximum

class Book:
    def __init__(self, root=ROOT):
        self.root = Path(root)
        require(not self.root.is_symlink(), 'unsafe_directory')
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        require(self.root.stat().st_uid == os.getuid(), 'unsafe_directory')
        os.chmod(self.root, 0o700)
        self.key_path = self.root / 'key'
        with self.locked():
            if not self.key_path.exists():
                self.write('key', AESGCM.generate_key(bit_length=256))
            self.key = self.read('key')
        require(len(self.key) == 32, 'invalid_key')

    def read(self, name):
        path = self.root / name
        require(not path.is_symlink(), 'unsafe_file')
        with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW), 'rb') as stream:
            st = os.fstat(stream.fileno())
            require(stat.S_ISREG(st.st_mode) and st.st_uid == os.getuid() and not st.st_mode & 0o077, 'unsafe_file')
            return stream.read(MAX_BYTES * 4)

    def write(self, name, data):
        path = self.root / name
        require(not path.is_symlink(), 'unsafe_file')
        temporary = self.root / ('.tmp-' + str(uuid.uuid4()))
        try:
            with os.fdopen(os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600), 'wb') as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, path)
            fd = os.open(self.root, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)
        finally:
            temporary.unlink(missing_ok=True)

    def encrypt(self, value, aad):
        nonce = os.urandom(12)
        return nonce + AESGCM(self.key).encrypt(nonce, encode(value), aad.encode())

    def decrypt(self, value, aad):
        require(len(value) >= 28, 'invalid_archive')
        return json.loads(AESGCM(self.key).decrypt(value[:12], value[12:], aad.encode()))

    @contextlib.contextmanager
    def locked(self):
        fd = os.open(self.root / 'lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX)
            yield
        finally:
            os.close(fd)

    def state(self):
        return self.decrypt(self.read('pending.enc'), 'pending-v1')

    def save(self, state):
        self.write('pending.enc', self.encrypt(state, 'pending-v1'))

    def begin(self, device, contact_ref='', names_only=False, direct=False, query='', offset=0):
        identifier(device)
        if contact_ref:
            identifier(contact_ref)
        require(type(names_only) is bool and not (names_only and contact_ref), 'invalid_export_mode')
        require(type(direct) is bool and not (direct and names_only), 'invalid_export_mode')
        if direct:
            require(isinstance(query, str) and bool(query) != bool(contact_ref) and len(query) <= 80 and
                    integer(offset, 0, 10000) and offset % 20 == 0 and not (contact_ref and offset), 'invalid_selection')
        else:
            require(not query and offset == 0, 'invalid_export_mode')
        require(not (self.root / 'pending.enc').exists(), 'update_already_pending')
        state = {'device': device, 'mode': 'names' if names_only else 'number' if contact_ref else 'full', 'chunks': [], 'started': int(time.time() * 1000)}
        if direct:
            self.next(state, 'get_contact', {'name': query, 'contact_ref': contact_ref, 'offset': offset})
        else:
            self.next(state, 'contacts_export_start', {} if names_only else {'contact_ref': contact_ref})
        return self.status()

    def next(self, state, operation, params):
        state['remote_state'] = 'pending'
        state.pop('progress', None)
        state['pending'] = {'operation': operation, 'queued': False,
                            'args': {'device_id': state['device'], **params, 'request_id': str(uuid.uuid4())}}
        self.save(state)  # Persist request identity BEFORE issuing a tool call.

    def plan(self):
        state = self.state()
        pending = state['pending']
        args = pending['args']
        if pending['queued']:
            return {'tool': 'jarviswake_status_result', 'args': {k: args[k] for k in ('device_id', 'request_id')}}
        tool = 'jarviswake_contacts_names_export_start' if state['mode'] == 'names' and pending['operation'] == 'contacts_export_start' else 'jarviswake_' + pending['operation']
        return {'tool': tool, 'args': args}

    def lookup(self, device, query):
        # One local step: inspect the names index, journal one direct request, return its plan.
        identifier(device)
        require(isinstance(query, str) and query.strip() and len(query) <= 80, 'invalid_selection')
        names = self.search_names(query)
        exact_matches = [c for c in names['names'] if c['match'] == 'exact']
        if names['truncated'] or len(exact_matches) > 1 or (not exact_matches and names['names']):
            return {'selection_required': True, 'candidates': exact_matches or names['names'], 'truncated': names['truncated']}
        selected = exact_matches[0]['name'] if exact_matches else query
        self.begin(device, direct=True, query=selected)
        return {**self.plan(), 'selection_required': False}

    def accept(self, response, show_number=False):
        state = self.state()
        pending = state['pending']
        args = pending['args']
        require(type(show_number) is bool and (not show_number or pending['operation'] == 'get_contact'), 'invalid_output_mode')
        require(response.get('device_id') == state['device'] and response.get('request_id') == args['request_id'], 'wrong_response')
        if response.get('state') in ('pending', 'running', 'awaiting_device'):
            pending['queued'] = True
            state['remote_state'] = response['state']
            progress = response.get('progress')
            if progress is not None:
                require(isinstance(progress, dict) and set(progress) == {'stage', 'observed_at'} and
                        progress['stage'] in ('starting', 'contacts', 'phones', 'emails', 'verifying', 'freezing') and
                        integer(progress['observed_at'], 1, int(time.time()*1000)+60000), 'invalid_progress')
                state['progress'] = progress
            self.save(state)
            return self.status()
        require(response.get('state') == 'responded' and response.get('source') == 'paired_device' and
                response.get('operation') == pending['operation'], 'export_not_completed')
        result = response.get('result', {})
        if pending['operation'] == 'get_contact':
            if result.get('code') == 'contacts_found':
                exact(result, 'code contacts truncated next_offset')
                require(isinstance(result['contacts'], list) and len(result['contacts']) <= 20 and
                        type(result['truncated']) is bool and result['truncated'] == (result['next_offset'] is not None))
                require(not result['truncated'] or (len(result['contacts']) == 20 and
                        type(result['next_offset']) is int and result['next_offset'] == args['offset'] + 20))
                for contact in result['contacts']:
                    exact(contact, 'contact_ref name label suffix')
                    identifier(contact['contact_ref'])
                    require(isinstance(contact['name'], str) and len(contact['name']) <= 100 and
                            isinstance(contact['label'], str) and len(contact['label']) <= 40 and
                            isinstance(contact['suffix'], str) and re.fullmatch('[0-9]{0,4}', contact['suffix']))
                (self.root / 'pending.enc').unlink()
                return {**self.status(), 'selection_required': bool(result['contacts']), 'code': 'contacts_found',
                        'candidates': result['contacts'], 'next_offset': result['next_offset']}
            exact(result, 'code bytes sha256 data_base64')
            require(result['code'] == 'contact_number' and integer(result['bytes'], 1, 8192) and
                    isinstance(result['sha256'], str) and re.fullmatch('[a-f0-9]{64}', result['sha256']) and
                    isinstance(result['data_base64'], str) and len(result['data_base64']) <= 10924)
            try:
                raw = base64.b64decode(result['data_base64'], validate=True)
            except (ValueError, TypeError):
                raise BookError('invalid_chunk') from None
            require(len(raw) == result['bytes'] and hashlib.sha256(raw).hexdigest() == result['sha256'], 'hash_mismatch')
            body = self.validate(json.loads(raw.decode('utf-8')), {'mode': 'number', 'contacts': 1, 'phones': 1, 'emails': 0})
            self.apply(state['device'], body)
            (self.root / 'pending.enc').unlink()
            result = self.status()
            if show_number:
                selected = body['contacts'][0]
                phone = selected['phones'][0]
                result['selected_contact'] = {'name': selected['name'], 'number': phone['number'], 'label': phone['label']}
            return result
        if 'manifest' in state:
            require(state['manifest']['expires_at'] > int(time.time()*1000), 'snapshot_expired')
        if pending['operation'] == 'contacts_export_start':
            exact(result, 'code snapshot_id bytes chunk_bytes sha256 contacts phones emails mode expires_at')
            require(result['code'] == 'contacts_export_ready', 'export_not_ready')
            identifier(result['snapshot_id'])
            require(result['mode'] == state['mode'] and result['chunk_bytes'] in (LEGACY_CHUNK, CHUNK) and
                    integer(result['bytes'], 1, MAX_BYTES) and isinstance(result['sha256'], str) and
                    re.fullmatch('[a-f0-9]{64}', result['sha256']))
            for field, maximum in [('contacts', 100000), ('phones', 1000000), ('emails', 1000000)]:
                require(integer(result[field], 0, maximum))
            require(state['mode'] != 'names' or (result['phones'] == 0 and result['emails'] == 0), 'invalid_names_export')
            require(integer(result['expires_at'], int(time.time()*1000)+1, int(time.time()*1000)+1805000))
            state['manifest'] = result
            self.next(state, 'contacts_export_page', {'snapshot_id': result['snapshot_id'], 'offset': 0})
        else:
            manifest = state['manifest']
            chunk_bytes = manifest['chunk_bytes']
            exact(result, 'code snapshot_id offset next_offset data_base64 expires_at')
            require(result['code'] == 'contacts_export_page' and result['snapshot_id'] == manifest['snapshot_id'] and
                    result['offset'] == args['offset'] and
                    integer(result['expires_at'], manifest['expires_at'], int(time.time()*1000)+1805000))
            manifest['expires_at'] = result['expires_at']
            require(isinstance(result['data_base64'], str) and len(result['data_base64']) <= 4 * ((chunk_bytes + 2) // 3))
            try:
                chunk = base64.b64decode(result['data_base64'], validate=True)
            except (ValueError, TypeError):
                raise BookError('invalid_chunk') from None
            require(len(chunk) == min(chunk_bytes, manifest['bytes'] - args['offset']))
            end = args['offset'] + len(chunk)
            require(result['next_offset'] == (end if end < manifest['bytes'] else None))
            state['chunks'].append(result['data_base64'])
            if end < manifest['bytes']:
                self.next(state, 'contacts_export_page', {'snapshot_id': manifest['snapshot_id'], 'offset': end})
            else:
                raw = b''.join(base64.b64decode(c, validate=True) for c in state['chunks'])
                require(len(raw) == manifest['bytes'] and hashlib.sha256(raw).hexdigest() == manifest['sha256'], 'hash_mismatch')
                body = self.validate(json.loads(raw.decode('utf-8')), manifest)
                self.apply(state['device'], body)
                (self.root / 'pending.enc').unlink()  # Complete, transaction committed.
        return self.status()

    def validate(self, body, manifest):
        exact(body, 'schema mode contacts')
        require(type(body['schema']) is int and body['schema'] == 1 and body['mode'] == manifest['mode'] and isinstance(body['contacts'], list))
        ids = set()
        counts = {'contacts': len(body['contacts']), 'phones': 0, 'emails': 0}
        for contact in body['contacts']:
            exact(contact, 'id lookup_key name' if body['mode'] == 'names' else 'id lookup_key name phones emails')
            for k in ('id', 'lookup_key', 'name'):
                require(isinstance(contact[k], str) and len(contact[k]) <= 8192)
            require(contact['id'] and contact['id'] not in ids)
            ids.add(contact['id'])
            if body['mode'] == 'names':
                continue
            for field, value in [('phones', 'number'), ('emails', 'address')]:
                require(isinstance(contact[field], list))
                row_ids = set()
                for row in contact[field]:
                    exact(row, 'id label ' + value)
                    require(all(isinstance(v, str) and len(v) <= 8192 for v in row.values()))
                    require(row['id'] and row['id'] not in row_ids)
                    row_ids.add(row['id'])
                counts[field] += len(contact[field])
        require(all(counts[k] == manifest[k] for k in counts), 'count_mismatch')
        require(body['mode'] != 'number' or counts == {'contacts': 1, 'phones': 1, 'emails': 0})
        return body

    def connect(self):
        path = self.root / 'book.sqlite3'
        require(not path.is_symlink(), 'unsafe_file')
        fd = os.open(path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        info = os.fstat(fd)
        os.close(fd)
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid() and not info.st_mode & 0o077, 'unsafe_file')
        db = sqlite3.connect(path)
        db.execute('PRAGMA journal_mode=DELETE')
        db.execute('PRAGMA secure_delete=ON')
        db.execute('CREATE TABLE IF NOT EXISTS contacts (key TEXT PRIMARY KEY, data BLOB NOT NULL)')
        db.execute('CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, data BLOB NOT NULL)')
        db.execute('CREATE TABLE IF NOT EXISTS name_contacts (key TEXT PRIMARY KEY, data BLOB NOT NULL)')
        return db

    def apply(self, device, body):
        db = self.connect()
        try:
            with db:
                meta_row = db.execute("SELECT data FROM metadata WHERE key='state'").fetchone()
                meta = self.decrypt(meta_row[0], 'metadata') if meta_row else {}
                require(not meta or meta['device'] == device, 'different_source_device')
                names_row = db.execute("SELECT data FROM metadata WHERE key='names_state'").fetchone()
                names_meta = self.decrypt(names_row[0], 'names_metadata') if names_row else {}
                require(not names_meta or names_meta['device'] == device, 'different_source_device')
                if body['mode'] == 'names':
                    db.execute('DELETE FROM name_contacts')
                    for contact in body['contacts']:
                        key = hashlib.sha256(encode([device, contact['id'], contact['lookup_key']])).hexdigest()
                        db.execute('INSERT INTO name_contacts VALUES (?,?)', (key, self.encrypt(contact, 'names:' + key)))
                    names_meta = {'device': device, 'names_updated_at': int(time.time()*1000)}
                    db.execute("INSERT OR REPLACE INTO metadata VALUES ('names_state',?)", (self.encrypt(names_meta, 'names_metadata'),))
                    return  # Names never replace or mutate the complete/single-number copy.
                if body['mode'] == 'full':
                    db.execute('DELETE FROM contacts')
                for contact in body['contacts']:
                    # Key is opaque: neither Android IDs nor names/numbers are stored in clear SQLite indexes.
                    key = hashlib.sha256(encode([device, contact['id'], contact['lookup_key']])).hexdigest()
                    previous = db.execute('SELECT data FROM contacts WHERE key=?', (key,)).fetchone()
                    if body['mode'] == 'number' and previous:
                        old = self.decrypt(previous[0], key)
                        updated = contact['phones'][0]
                        old['phones'] = [p for p in old['phones'] if p['id'] != updated['id']] + [updated]
                        old['name'] = contact['name']
                        contact = old  # Preserve other numbers and all emails.
                    db.execute('INSERT OR REPLACE INTO contacts VALUES (?,?)', (key, self.encrypt(contact, key)))
                meta.update(device=device, updated_at=int(time.time()*1000), last_mode=body['mode'])
                if body['mode'] == 'full':
                    meta['full_updated_at'] = meta['updated_at']
                db.execute("INSERT OR REPLACE INTO metadata VALUES ('state',?)", (self.encrypt(meta, 'metadata'),))
        finally:
            db.close()

    def status(self):
        result = {'pending': (self.root / 'pending.enc').exists(), 'contacts': 0, 'phones': 0, 'emails': 0,
                  'full_updated_at': None, 'updated_at': None, 'names': 0, 'names_updated_at': None}
        if result['pending']:
            pending = self.state()
            result.update(remote_state=pending.get('remote_state', 'pending'), progress=pending.get('progress'))
        if not (self.root / 'book.sqlite3').exists():
            return result
        db = self.connect()
        try:
            meta = db.execute("SELECT data FROM metadata WHERE key='state'").fetchone()
            if meta:
                result.update(self.decrypt(meta[0], 'metadata'))
            names_meta = db.execute("SELECT data FROM metadata WHERE key='names_state'").fetchone()
            if names_meta:
                result['names_updated_at'] = self.decrypt(names_meta[0], 'names_metadata')['names_updated_at']
            result['names'] = db.execute('SELECT COUNT(*) FROM name_contacts').fetchone()[0]
            for key, encrypted in db.execute('SELECT key,data FROM contacts'):
                contact = self.decrypt(encrypted, key)
                result['contacts'] += 1
                result['phones'] += len(contact['phones'])
                result['emails'] += len(contact['emails'])
        finally:
            db.close()
        return result

    def search(self, query):
        require(isinstance(query, str) and query.strip(), 'query_required')
        if not (self.root / 'book.sqlite3').exists():
            return {'contacts': [], 'updated_at': None}
        db = self.connect()
        matches = []
        try:
            for key, encrypted in db.execute('SELECT key,data FROM contacts'):
                contact = self.decrypt(encrypted, key)
                if any(query.casefold() in value.casefold() for value in ([contact['name']] +
                        [p['number'] for p in contact['phones']] + [e['address'] for e in contact['emails']])):
                    matches.append(contact)
                    if len(matches) == 20:
                        break
        finally:
            db.close()
        return {'contacts': matches, 'updated_at': self.status()['updated_at'], 'limit': 20}

    def search_names(self, query):
        require(isinstance(query, str) and query.strip() and len(query) <= 256, 'query_required')
        def normalize(value):
            value = ''.join(c for c in unicodedata.normalize('NFKD', value.casefold()) if not unicodedata.combining(c))
            return ' '.join(''.join(c if c.isalnum() else ' ' for c in value).split())
        wanted = normalize(query)
        require(wanted, 'query_required')
        matches = []
        if (self.root / 'book.sqlite3').exists():
            db = self.connect()
            try:
                for key, encrypted in db.execute('SELECT key,data FROM name_contacts'):
                    contact = self.decrypt(encrypted, 'names:' + key)
                    name = normalize(contact['name'])
                    if not name:
                        continue
                    score = max(SequenceMatcher(None, wanted, name, autojunk=False).ratio(),
                                SequenceMatcher(None, ' '.join(sorted(wanted.split())), ' '.join(sorted(name.split())), autojunk=False).ratio())
                    if wanted in name.split():
                        score = max(score, 0.9)
                    if score >= 0.65:
                        matches.append({**contact, 'score': round(score, 4), 'match': 'exact' if name == wanted else 'similar'})
            finally:
                db.close()
        matches.sort(key=lambda c: (-c['score'], c['name'], c['id']))
        return {'names': matches[:20], 'names_updated_at': self.status()['names_updated_at'], 'limit': 20,
                'truncated': len(matches) > 20, 'requires_phone_verification': True}

def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['begin', 'lookup', 'plan', 'accept', 'status', 'search', 'search-names', 'abort'])
    parser.add_argument('--device-id')
    parser.add_argument('--contact-ref', default='')
    parser.add_argument('--query')
    parser.add_argument('--names-only', action='store_true')
    parser.add_argument('--direct', action='store_true')
    parser.add_argument('--offset', type=int, default=0)
    parser.add_argument('--show-number', action='store_true', help='Return only the verified selected contact on direct accept')
    args = parser.parse_args()
    try:
        book = Book()
        with book.locked():
            if args.command == 'begin':
                result = book.begin(args.device_id, args.contact_ref, args.names_only, args.direct, args.query or '', args.offset)
            elif args.command == 'lookup':
                result = book.lookup(args.device_id, args.query)
            elif args.command == 'accept':
                data = sys.stdin.buffer.read(MAX_RESPONSE_BYTES + 1)
                require(len(data) <= MAX_RESPONSE_BYTES, 'response_too_large')
                response = json.loads(data)
                result = book.accept(response, show_number=getattr(args, 'show_number', False))
            elif args.command == 'abort':
                (book.root / 'pending.enc').unlink(missing_ok=True)
                result = book.status()
            elif args.command == 'search':
                result = book.search(args.query)
            elif args.command == 'search-names':
                result = book.search_names(args.query)
            else:
                result = getattr(book, args.command)()
        print(json.dumps(result, ensure_ascii=False))
    except Exception as error:
        # No provider/contact payload, SQL exception, crypto error or traceback in diagnostics.
        print(json.dumps({'error': str(error) if isinstance(error, BookError) else 'local_book_unavailable'}))
        return 1
    return 0

if __name__ == '__main__':
    raise SystemExit(main())



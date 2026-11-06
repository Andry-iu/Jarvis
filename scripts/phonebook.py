#!/usr/bin/env python3
"""One-way phone book mirror. Transport is the existing authenticated JarvisWake MCP plugin.

No phone tokens here. Feed tool results on stdin; CLI output contains only plans/counts,
except an explicit nonempty search. No unencrypted contact content is written to disk.
"""
import argparse
import base64
import contextlib
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
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = Path('/workspace/.jarvis-private/phonebook')
MAX_BYTES = 8 * 1024 * 1024
CHUNK = 6144
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

    def begin(self, device, contact_ref=''):
        identifier(device)
        if contact_ref:
            identifier(contact_ref)
        require(not (self.root / 'pending.enc').exists(), 'update_already_pending')
        state = {'device': device, 'mode': 'number' if contact_ref else 'full', 'chunks': [], 'started': int(time.time() * 1000)}
        self.next(state, 'contacts_export_start', {'contact_ref': contact_ref})
        return self.status()

    def next(self, state, operation, params):
        state['pending'] = {'operation': operation, 'queued': False,
                            'args': {'device_id': state['device'], **params, 'request_id': str(uuid.uuid4())}}
        self.save(state)  # Persist request identity BEFORE issuing a tool call.

    def plan(self):
        state = self.state()
        pending = state['pending']
        args = pending['args']
        if pending['queued']:
            return {'tool': 'jarviswake_status_result', 'args': {k: args[k] for k in ('device_id', 'request_id')}}
        return {'tool': 'jarviswake_' + pending['operation'], 'args': args}

    def accept(self, response):
        state = self.state()
        pending = state['pending']
        args = pending['args']
        require(response.get('device_id') == state['device'] and response.get('request_id') == args['request_id'], 'wrong_response')
        if response.get('state') == 'pending':
            pending['queued'] = True
            self.save(state)
            return self.status()
        require(response.get('state') == 'responded' and response.get('source') == 'paired_device' and
                response.get('operation') == pending['operation'], 'export_not_completed')
        result = response.get('result', {})
        if 'manifest' in state:
            require(state['manifest']['expires_at'] > int(time.time()*1000), 'snapshot_expired')
        if pending['operation'] == 'contacts_export_start':
            exact(result, 'code snapshot_id bytes chunk_bytes sha256 contacts phones emails mode expires_at')
            require(result['code'] == 'contacts_export_ready', 'export_not_ready')
            identifier(result['snapshot_id'])
            require(result['mode'] == state['mode'] and result['chunk_bytes'] == CHUNK and
                    integer(result['bytes'], 1, MAX_BYTES) and isinstance(result['sha256'], str) and
                    re.fullmatch('[a-f0-9]{64}', result['sha256']))
            for field, maximum in [('contacts', 100000), ('phones', 1000000), ('emails', 1000000)]:
                require(integer(result[field], 0, maximum))
            require(integer(result['expires_at'], int(time.time()*1000)+1, int(time.time()*1000)+1805000))
            state['manifest'] = result
            self.next(state, 'contacts_export_page', {'snapshot_id': result['snapshot_id'], 'offset': 0})
        else:
            manifest = state['manifest']
            exact(result, 'code snapshot_id offset next_offset data_base64 expires_at')
            require(result['code'] == 'contacts_export_page' and result['snapshot_id'] == manifest['snapshot_id'] and
                    result['offset'] == args['offset'] and result['expires_at'] == manifest['expires_at'])
            require(isinstance(result['data_base64'], str) and len(result['data_base64']) <= 8192)
            try:
                chunk = base64.b64decode(result['data_base64'], validate=True)
            except (ValueError, TypeError):
                raise BookError('invalid_chunk') from None
            require(len(chunk) == min(CHUNK, manifest['bytes'] - args['offset']))
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
            exact(contact, 'id lookup_key name phones emails')
            for k in ('id', 'lookup_key', 'name'):
                require(isinstance(contact[k], str) and len(contact[k]) <= 8192)
            require(contact['id'] and contact['id'] not in ids)
            ids.add(contact['id'])
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
        return db

    def apply(self, device, body):
        db = self.connect()
        try:
            with db:
                meta_row = db.execute("SELECT data FROM metadata WHERE key='state'").fetchone()
                meta = self.decrypt(meta_row[0], 'metadata') if meta_row else {}
                require(not meta or meta['device'] == device, 'different_source_device')
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
                  'full_updated_at': None, 'updated_at': None}
        if not (self.root / 'book.sqlite3').exists():
            return result
        db = self.connect()
        try:
            meta = db.execute("SELECT data FROM metadata WHERE key='state'").fetchone()
            if meta:
                result.update(self.decrypt(meta[0], 'metadata'))
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

def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['begin', 'plan', 'accept', 'status', 'search', 'abort'])
    parser.add_argument('--device-id')
    parser.add_argument('--contact-ref', default='')
    parser.add_argument('--query')
    args = parser.parse_args()
    try:
        book = Book()
        with book.locked():
            if args.command == 'begin':
                result = book.begin(args.device_id, args.contact_ref)
            elif args.command == 'accept':
                data = sys.stdin.buffer.read(32769)
                require(len(data) <= 32768, 'response_too_large')
                response = json.loads(data)
                result = book.accept(response)
            elif args.command == 'abort':
                (book.root / 'pending.enc').unlink(missing_ok=True)
                result = book.status()
            elif args.command == 'search':
                result = book.search(args.query)
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

import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import time
import unittest
import uuid

spec = importlib.util.spec_from_file_location('phonebook', Path(__file__).parents[1] / 'phonebook.py')
m = importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

def contact(cid='1', number='+39000001', email='private@example.invalid'):
    return {'id': cid, 'lookup_key': 'lookup' + cid, 'name': 'Private Contact ' + cid,
            'phones': [{'id': cid+'1', 'number': number, 'label': 'mobile'}],
            'emails': [{'id': cid+'2', 'address': email, 'label': 'home'}] if email else []}

class PhonebookTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.book = m.Book(Path(self.tmp.name)/'book');self.device = str(uuid.uuid4())
    def response(self, result):
        p = self.book.state()['pending']
        return {'state': 'responded', 'source': 'paired_device', 'device_id': self.device,
                'request_id': p['args']['request_id'], 'operation': p['operation'], 'result': result}
    def start(self, contacts, mode='full', digest=None):
        self.book.begin(self.device, '' if mode=='full' else str(uuid.uuid4()))
        raw = m.encode({'schema': 1, 'mode': mode, 'contacts': contacts})
        manifest = {'code':'contacts_export_ready','snapshot_id':str(uuid.uuid4()),'bytes':len(raw),'chunk_bytes':m.CHUNK,
                    'sha256':digest or hashlib.sha256(raw).hexdigest(),'mode':mode,
                    'contacts':len(contacts),'phones':sum(len(c['phones']) for c in contacts),
                    'emails':sum(len(c['emails']) for c in contacts),'expires_at':int(time.time()*1000)+100000}
        self.book.accept(self.response(manifest))
        return raw, manifest
    def finish(self, raw, manifest):
        for offset in range(0,len(raw),m.CHUNK):
            chunk=raw[offset:offset+m.CHUNK];end=offset+len(chunk)
            self.book.accept(self.response({'code':'contacts_export_page','snapshot_id':manifest['snapshot_id'],
                'offset':offset,'next_offset':None if end==len(raw) else end,'expires_at':manifest['expires_at'],
                'data_base64':base64.b64encode(chunk).decode()}))
    def test_full_sync_removes_only_local_deleted_records_and_files_are_private_ciphertext(self):
        raw, meta=self.start([contact(),contact('2')]);self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],2)
        raw, meta=self.start([contact('2', '+39000222')]);self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],1)
        self.assertEqual(self.book.search('Private')['contacts'][0]['phones'][0]['number'],'+39000222')
        for p in self.book.root.iterdir():
            self.assertEqual(p.stat().st_mode & 0o077, 0)
            self.assertNotIn(b'Private Contact',p.read_bytes());self.assertNotIn(b'private@example',p.read_bytes())
    def test_single_requested_number_preserves_other_numbers_emails_and_people(self):
        a=contact();a['phones'].append({'id':'extra','number':'55555','label':'work'})
        raw,meta=self.start([a,contact('2')]);self.finish(raw,meta)
        raw,meta=self.start([contact(number='99999',email='')],mode='number');self.finish(raw,meta)
        result=self.book.search('Contact 1')['contacts'][0]
        self.assertEqual({p['number'] for p in result['phones']},{'99999','55555'})
        self.assertEqual(len(result['emails']),1);self.assertEqual(self.book.status()['contacts'],2)
    def test_hash_failure_keeps_last_complete_book_and_pending_request_is_stable_after_reopen(self):
        raw,meta=self.start([contact()]);self.finish(raw,meta)
        raw,meta=self.start([],digest='0'*64)
        plan=self.book.plan();self.assertEqual(m.Book(self.book.root).plan(),plan)
        with self.assertRaisesRegex(m.BookError,'hash_mismatch'):self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],1)
    def test_wrong_duplicate_and_expired_responses_do_not_advance_or_damage_copy(self):
        raw,meta=self.start([contact()]);plan=self.book.plan()
        bad=self.response({});bad['request_id']=str(uuid.uuid4())
        with self.assertRaisesRegex(m.BookError,'wrong_response'):self.book.accept(bad)
        self.assertEqual(self.book.plan(),plan)
        state=self.book.state();state['manifest']['expires_at']=1;self.book.save(state)
        with self.assertRaisesRegex(m.BookError,'snapshot_expired'):self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],0)
    def test_pending_changes_only_poll_plan_and_never_creates_second_export(self):
        self.book.begin(self.device);plan=self.book.plan();args=plan['args']
        self.book.accept({'state':'pending','device_id':self.device,'request_id':args['request_id']})
        self.assertEqual(self.book.plan()['tool'],'jarviswake_status_result')
        self.assertEqual(self.book.plan()['args']['request_id'],args['request_id'])
        with self.assertRaisesRegex(m.BookError,'update_already_pending'):self.book.begin(self.device)
    def test_non_ascii_multichunk_snapshot_and_same_name_records_are_not_merged(self):
        a=contact();a['name']='à'*8000
        b=contact('2');b['name']=a['name']
        raw,meta=self.start([a,b]);self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],2)
    def test_invalid_counts_duplicate_ids_and_different_source_do_not_replace_good_book(self):
        raw,meta=self.start([contact()]);self.finish(raw,meta)
        raw,meta=self.start([contact(),contact()])
        with self.assertRaises(m.BookError):self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],1)
        self.book.root.joinpath('pending.enc').unlink()
        self.device=str(uuid.uuid4());raw,meta=self.start([])
        with self.assertRaisesRegex(m.BookError,'different_source_device'):self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],1)
    def test_out_of_order_page_and_count_mismatch_do_not_replace_a_good_copy(self):
        raw,meta=self.start([contact()]);self.finish(raw,meta)
        raw,meta=self.start([contact('2')])
        response=self.response({'code':'contacts_export_page','snapshot_id':meta['snapshot_id'],
            'offset':6144,'next_offset':None,'expires_at':meta['expires_at'],'data_base64':base64.b64encode(raw).decode()})
        with self.assertRaises(m.BookError):self.book.accept(response)
        self.assertEqual(self.book.status()['contacts'],1)
        state=self.book.state();state['manifest']['contacts']=2;self.book.save(state)
        with self.assertRaisesRegex(m.BookError,'count_mismatch'):self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],1)
    def test_interrupted_staging_is_encrypted_and_reopens_without_changing_request_id(self):
        a=contact();a['name']='Private Contact '+('à'*8000)
        raw,meta=self.start([a])
        response=self.response({'code':'contacts_export_page','snapshot_id':meta['snapshot_id'],
            'offset':0,'next_offset':m.CHUNK,'expires_at':meta['expires_at'],
            'data_base64':base64.b64encode(raw[:m.CHUNK]).decode()})
        self.book.accept(response)
        self.assertNotIn(b'Private Contact',self.book.root.joinpath('pending.enc').read_bytes())
        plan=self.book.plan();self.book=m.Book(self.book.root)
        self.assertEqual(self.book.plan(),plan)
        with self.assertRaisesRegex(m.BookError,'wrong_response'):self.book.accept(response)
        for offset in range(m.CHUNK,len(raw),m.CHUNK):
            chunk=raw[offset:offset+m.CHUNK];end=offset+len(chunk)
            self.book.accept(self.response({'code':'contacts_export_page','snapshot_id':meta['snapshot_id'],
                'offset':offset,'next_offset':None if end==len(raw) else end,'expires_at':meta['expires_at'],
                'data_base64':base64.b64encode(chunk).decode()}))
        self.assertEqual(self.book.status()['contacts'],1)

    def test_symlink_archive_and_open_key_permissions_are_rejected(self):
        other=Path(self.tmp.name)/'other';other.write_text('keep')
        (self.book.root/'book.sqlite3').symlink_to(other)
        with self.assertRaisesRegex(m.BookError,'unsafe_file'):self.book.status()
        self.assertEqual(other.read_text(),'keep')
        self.book.key_path.chmod(0o644)
        with self.assertRaisesRegex(m.BookError,'unsafe_file'):m.Book(self.book.root)

if __name__=='__main__':unittest.main()

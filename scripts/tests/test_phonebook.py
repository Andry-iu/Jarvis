import base64
import io
from types import SimpleNamespace
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import time
import unittest
import uuid
from unittest import mock

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
    def start(self, contacts, mode='full', digest=None, chunk_bytes=m.CHUNK):
        self.book.begin(self.device, str(uuid.uuid4()) if mode=='number' else '', names_only=mode=='names')
        raw = m.encode({'schema': 1, 'mode': mode, 'contacts': contacts})
        manifest = {'code':'contacts_export_ready','snapshot_id':str(uuid.uuid4()),'bytes':len(raw),'chunk_bytes':chunk_bytes,
                    'sha256':digest or hashlib.sha256(raw).hexdigest(),'mode':mode,
                    'contacts':len(contacts),'phones':sum(len(c.get('phones', [])) for c in contacts),
                    'emails':sum(len(c.get('emails', [])) for c in contacts),'expires_at':int(time.time()*1000)+100000}
        self.book.accept(self.response(manifest))
        return raw, manifest
    def finish(self, raw, manifest):
        for offset in range(0,len(raw),manifest['chunk_bytes']):
            chunk=raw[offset:offset+manifest['chunk_bytes']];end=offset+len(chunk)
            self.book.accept(self.response({'code':'contacts_export_page','snapshot_id':manifest['snapshot_id'],
                'offset':offset,'next_offset':None if end==len(raw) else end,'expires_at':manifest['expires_at'],
                'data_base64':base64.b64encode(chunk).decode()}))
    def direct_result(self, contacts=None):
        raw = m.encode({'schema': 1, 'mode': 'number', 'contacts': contacts or [contact(email='')]})
        return {'code': 'contact_number', 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(),
                'data_base64': base64.b64encode(raw).decode()}

    def test_lookup_combines_exact_index_choice_and_persisted_plan(self):
        names=[{'id':'1','lookup_key':'lookup1','name':'Élodie Example'}]
        raw,meta=self.start(names,mode='names');self.finish(raw,meta)
        result=self.book.lookup(self.device,'Elodie Example')
        self.assertFalse(result['selection_required'])
        self.assertEqual(result['args']['name'],'Élodie Example')
        self.assertEqual(result['args']['request_id'],self.book.plan()['args']['request_id'])

    def test_lookup_never_selects_a_similar_candidate_or_homonym(self):
        for names,query in [([{'id':'1','lookup_key':'one','name':'Marta Example'}],'Martta Example'),
                            ([{'id':'1','lookup_key':'one','name':'Marta Example'},
                              {'id':'2','lookup_key':'two','name':'Marta Example'}],'Marta Example')]:
            raw,meta=self.start(names,mode='names');self.finish(raw,meta)
            result=self.book.lookup(self.device,query)
            self.assertTrue(result['selection_required']);self.assertFalse(self.book.status()['pending'])

    def test_lookup_with_no_index_match_keeps_literal_user_name(self):
        result=self.book.lookup(self.device,'Exact user spelling')
        self.assertEqual(result['args']['name'],'Exact user spelling')
        self.assertEqual(result['tool'],'jarviswake_get_contact')

    def test_direct_accept_can_return_verified_number_without_another_search(self):
        self.book.lookup(self.device,'Private Contact 1')
        answer=self.book.accept(self.response(self.direct_result()),show_number=True)
        self.assertEqual(answer['selected_contact'],{'name':'Private Contact 1','number':'+39000001','label':'mobile'})
        self.assertFalse(answer['pending'])

    def test_direct_accept_never_discloses_corrupt_or_wrong_request_payload(self):
        self.book.lookup(self.device,'Private Contact 1')
        response=self.response(self.direct_result());response['request_id']=str(uuid.uuid4())
        with self.assertRaises(m.BookError):self.book.accept(response,show_number=True)
        response=self.response(self.direct_result());response['result']['sha256']='0'*64
        with self.assertRaises(m.BookError):self.book.accept(response,show_number=True)
        self.assertEqual(self.book.status()['phones'],0)

    def test_direct_number_is_one_persisted_request_with_atomic_incremental_save(self):
        raw, meta = self.start([contact('old')]); self.finish(raw, meta)
        self.book.begin(self.device, direct=True, query='Private Contact 1')
        plan = self.book.plan()
        self.assertEqual(plan['tool'], 'jarviswake_get_contact')
        self.assertEqual(plan['args']['name'], 'Private Contact 1')
        self.assertEqual(m.Book(self.book.root).plan(), plan)
        result = self.book.accept(self.response(self.direct_result()))
        self.assertFalse(result['pending']); self.assertEqual(result['contacts'], 2)
        self.assertEqual(len(self.book.search('Private Contact 1')['contacts']), 1)
        for path in self.book.root.iterdir():
            self.assertNotIn(b'Private Contact', path.read_bytes())

    def test_direct_ambiguity_never_saves_masked_suffix_as_number(self):
        self.book.begin(self.device, direct=True, query='Example')
        candidate = {'contact_ref': str(uuid.uuid4()), 'name': 'Example', 'label': 'mobile', 'suffix': '0001'}
        result = self.book.accept(self.response({'code': 'contacts_found', 'contacts': [candidate], 'truncated': False, 'next_offset': None}))
        self.assertTrue(result['selection_required']); self.assertEqual(result['candidates'], [candidate])
        self.assertFalse(result['pending']); self.assertEqual(self.book.status()['phones'], 0)
        self.book.begin(self.device, contact_ref=candidate['contact_ref'], direct=True)
        self.assertEqual(self.book.plan()['args']['name'], '')
        self.book.accept(self.response(self.direct_result()))
        self.assertEqual(self.book.status()['phones'], 1)

    def test_direct_corrupt_hash_and_multiple_numbers_never_replace_previous_copy(self):
        raw, meta = self.start([contact('old')]); self.finish(raw, meta)
        self.book.begin(self.device, direct=True, query='Example')
        before = self.book.status()['contacts']
        bad = self.direct_result(); bad['sha256'] = '0' * 64
        with self.assertRaises(m.BookError): self.book.accept(self.response(bad))
        with self.assertRaises(m.BookError): self.book.accept(self.response(self.direct_result([contact(email=''), contact('2', email='')])))
        self.assertEqual(self.book.status()['contacts'], before)
        self.assertTrue(self.book.status()['pending'])

    def test_direct_waiting_keeps_same_uuid_and_wrong_source_cannot_save(self):
        self.book.begin(self.device, direct=True, query='Example')
        args = self.book.plan()['args']
        self.book.accept({'state': 'pending', 'device_id': self.device, 'request_id': args['request_id']})
        self.assertEqual(self.book.plan()['tool'], 'jarviswake_status_result')
        self.assertEqual(self.book.plan()['args']['request_id'], args['request_id'])
        bad = self.response(self.direct_result()); bad['source'] = 'untrusted'
        with self.assertRaises(m.BookError): self.book.accept(bad)
        self.assertEqual(self.book.status()['phones'], 0)

    def test_names_import_and_fuzzy_query_preserve_complete_numbers_and_homonyms(self):
        raw,meta=self.start([contact()]);self.finish(raw,meta)
        full_before=self.book.search('Private');stamp=self.book.status()['full_updated_at']
        names=[{'id':'1','lookup_key':'lookup1','name':'Marta Esempio'},
               {'id':'2','lookup_key':'lookup2','name':'Marta Esempio'},
               {'id':'3','lookup_key':'lookup3','name':'Élodie Esempio'},
               {'id':'4','lookup_key':'lookup4','name':''}]
        raw,meta=self.start(names,mode='names');self.finish(raw,meta)
        self.assertEqual(self.book.status()['names'],4)
        self.assertEqual(self.book.status()['full_updated_at'],stamp)
        self.assertEqual(self.book.search('Private'),full_before)
        result=self.book.search_names('Martta Esempio')
        self.assertEqual([c['name'] for c in result['names']],['Marta Esempio','Marta Esempio'])
        self.assertTrue(all(c['match']=='similar' for c in result['names']))
        self.assertTrue(result['requires_phone_verification'])
        self.assertEqual(self.book.search_names('Elodie Esempio')['names'][0]['match'],'exact')
        self.assertEqual(self.book.search_names('Esempio Marta')['names'][0]['score'],1.0)
        raw,meta=self.start([contact('3',email='')],mode='number');self.finish(raw,meta)
        self.assertEqual(self.book.status()['names'],4);self.assertEqual(self.book.status()['contacts'],2)
        for p in self.book.root.iterdir():
            self.assertNotIn(b'Marta Esempio',p.read_bytes())

    def test_names_only_plan_survives_resume_and_uses_dedicated_tool_without_contact_ref(self):
        self.book.begin(self.device,names_only=True);plan=self.book.plan()
        self.assertEqual(plan['tool'],'jarviswake_contacts_names_export_start')
        self.assertNotIn('contact_ref',plan['args'])
        self.assertEqual(m.Book(self.book.root).plan(),plan)
        self.book.accept({'state':'running',**plan['args']})
        self.assertEqual(self.book.plan()['tool'],'jarviswake_status_result')
        self.assertEqual(self.book.plan()['args']['request_id'],plan['args']['request_id'])

    def test_names_payload_rejects_numbers_extra_fields_and_bad_hash_without_replacing_index(self):
        name={'id':'1','lookup_key':'lookup1','name':'Marta Esempio'}
        raw,meta=self.start([name],mode='names');self.finish(raw,meta)
        raw,meta=self.start([{**name,'phones':[]}],mode='names')
        with self.assertRaises(m.BookError):self.finish(raw,meta)
        self.assertEqual(self.book.status()['names'],1)
        self.book.root.joinpath('pending.enc').unlink()
        raw,meta=self.start([],mode='names',digest='0'*64)
        with self.assertRaisesRegex(m.BookError,'hash_mismatch'):self.finish(raw,meta)
        self.assertEqual(self.book.status()['names'],1)

    def test_names_refresh_removes_only_old_names_and_never_claims_a_full_book(self):
        name={'id':'1','lookup_key':'lookup1','name':'Marta Esempio'}
        raw,meta=self.start([name],mode='names');self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],0);self.assertIsNone(self.book.status()['full_updated_at'])
        raw,meta=self.start([],mode='names');self.finish(raw,meta)
        self.assertEqual(self.book.status()['names'],0)
        self.assertEqual(self.book.search_names('Marta')['names'],[])
        with self.assertRaises(m.BookError):self.book.search_names(' ')
        with self.assertRaisesRegex(m.BookError,'invalid_export_mode'):
            self.book.begin(self.device,str(uuid.uuid4()),names_only=True)

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
        raw,meta=self.start([a],chunk_bytes=m.LEGACY_CHUNK)
        response=self.response({'code':'contacts_export_page','snapshot_id':meta['snapshot_id'],
            'offset':0,'next_offset':meta['chunk_bytes'],'expires_at':meta['expires_at'],
            'data_base64':base64.b64encode(raw[:meta['chunk_bytes']]).decode()})
        self.book.accept(response)
        self.assertNotIn(b'Private Contact',self.book.root.joinpath('pending.enc').read_bytes())
        plan=self.book.plan();self.book=m.Book(self.book.root)
        self.assertEqual(self.book.plan(),plan)
        with self.assertRaisesRegex(m.BookError,'wrong_response'):self.book.accept(response)
        for offset in range(meta['chunk_bytes'],len(raw),meta['chunk_bytes']):
            chunk=raw[offset:offset+meta['chunk_bytes']];end=offset+len(chunk)
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

    def test_long_running_and_missing_heartbeat_keep_the_same_request_without_claiming_completion(self):
        self.book.begin(self.device);args=self.book.plan()['args']
        for state in ('running', 'awaiting_device', 'running'):
            self.book.accept({'state':state,'device_id':self.device,'request_id':args['request_id'],
                              'progress':{'stage':'phones','observed_at':int(time.time()*1000)}})
            self.assertEqual(self.book.plan()['args']['request_id'], args['request_id'])
            self.assertEqual(self.book.status()['remote_state'], state)
            self.assertTrue(self.book.status()['pending'])
            self.assertEqual(self.book.status()['contacts'],0)
        self.assertEqual(m.Book(self.book.root).plan(),self.book.plan())

    def test_active_multichunk_transfer_renews_inactivity_lease_beyond_the_original_expiry(self):
        a=contact();a['name']='à'*8000
        raw,meta=self.start([a],chunk_bytes=m.LEGACY_CHUNK);initial=meta['expires_at']
        for offset in range(0,len(raw),meta['chunk_bytes']):
            now=initial-1000 if offset==0 else initial+1000
            expiry=now+1800000
            chunk=raw[offset:offset+meta['chunk_bytes']];end=offset+len(chunk)
            with mock.patch.object(m.time,'time',return_value=now/1000):
                self.book.accept(self.response({'code':'contacts_export_page','snapshot_id':meta['snapshot_id'],
                    'offset':offset,'next_offset':None if end==len(raw) else end,'expires_at':expiry,
                    'data_base64':base64.b64encode(chunk).decode()}))
        self.assertFalse(self.book.status()['pending']);self.assertEqual(self.book.status()['contacts'],1)

    def test_progress_with_private_or_unknown_fields_does_not_advance_the_import(self):
        self.book.begin(self.device);args=self.book.plan()['args'];plan=self.book.plan()
        with self.assertRaisesRegex(m.BookError,'invalid_progress'):
            self.book.accept({'state':'running','device_id':self.device,'request_id':args['request_id'],
                'progress':{'stage':'phones','observed_at':int(time.time()*1000),'number':'PRIVATE'}})
        self.assertEqual(self.book.plan(),plan);self.assertEqual(self.book.status()['contacts'],0)

    def test_256_kibibyte_boundaries_reassemble_atomically_and_match_hash(self):
        self.assertEqual(m.CHUNK, 262144)
        for target in (163483, m.CHUNK - 1, m.CHUNK, m.CHUNK + 1, 2 * m.CHUNK):
            with self.subTest(target=target):
                # Real schema with bounded strings; pad the final name to hit exact byte boundaries.
                rows=[{'id':str(i),'lookup_key':'k'+str(i),'name':'x'*7000} for i in range(target//7000)]
                while len(m.encode({'schema':1,'mode':'names','contacts':rows})) >= target:
                    rows.pop()
                overhead=len(m.encode({'schema':1,'mode':'names','contacts':rows+[{'id':'tail','lookup_key':'tail','name':''}]}))
                rows.append({'id':'tail','lookup_key':'tail','name':'x'*(target-overhead)})
                raw,meta=self.start(rows,mode='names')
                self.assertEqual(len(raw),target)
                pages=0
                for offset in range(0,len(raw),m.CHUNK):
                    part=raw[offset:offset+m.CHUNK];end=offset+len(part)
                    if offset:
                        self.book=m.Book(self.book.root)  # resume the same persisted request
                    reply=self.response({'code':'contacts_export_page','snapshot_id':meta['snapshot_id'],
                        'offset':offset,'next_offset':end if end<len(raw) else None,'expires_at':meta['expires_at'],
                        'data_base64':base64.b64encode(part).decode()})
                    self.assertLessEqual(len(m.encode(reply)),m.MAX_RESPONSE_BYTES)
                    self.book.accept(reply);pages+=1
                    self.assertEqual(self.book.status()['pending'],end<len(raw))
                self.assertEqual(pages,(target+m.CHUNK-1)//m.CHUNK)
                self.assertEqual(self.book.status()['names'],len(rows))

    def test_cli_accept_reads_a_full_256_kibibyte_page_with_base64_overhead(self):
        rows=[{'id':str(i),'lookup_key':'k'+str(i),'name':'x'*8000} for i in range(40)]
        raw,meta=self.start(rows,mode='names')
        reply=self.response({'code':'contacts_export_page','snapshot_id':meta['snapshot_id'],'offset':0,
            'next_offset':m.CHUNK,'expires_at':meta['expires_at'],'data_base64':base64.b64encode(raw[:m.CHUNK]).decode()})
        with mock.patch.object(m.sys,'argv',['phonebook.py','accept']), mock.patch.object(m,'Book',return_value=self.book), \
             mock.patch.object(m.sys,'stdin',SimpleNamespace(buffer=io.BytesIO(m.encode(reply)))), mock.patch.object(m.sys,'stdout',io.StringIO()):
            self.assertEqual(m.main(),0)
        self.assertTrue(self.book.status()['pending'])
        self.assertEqual(self.book.plan()['args']['offset'],m.CHUNK)
        remainder=raw[m.CHUNK:]
        self.book.accept(self.response({'code':'contacts_export_page','snapshot_id':meta['snapshot_id'],'offset':m.CHUNK,
            'next_offset':None,'expires_at':meta['expires_at'],'data_base64':base64.b64encode(remainder).decode()}))
        self.assertEqual(self.book.status()['names'],len(rows))

    def test_large_corrupt_page_preserves_previous_copy_and_unsupported_chunk_is_rejected(self):
        raw,meta=self.start([contact()]);self.finish(raw,meta)
        rows=[{'id':str(i),'lookup_key':'k'+str(i),'name':'x'*8000} for i in range(150)]
        raw,meta=self.start(rows,mode='names',digest='0'*64)
        with self.assertRaisesRegex(m.BookError,'hash_mismatch'):
            self.finish(raw,meta)
        self.assertEqual(self.book.status()['contacts'],1)
        self.assertEqual(self.book.status()['names'],0)
        self.book.root.joinpath('pending.enc').unlink()
        self.book.begin(self.device,names_only=True)
        meta['chunk_bytes']=2*m.CHUNK
        with self.assertRaises(m.BookError):self.book.accept(self.response(meta))

if __name__=='__main__':unittest.main()



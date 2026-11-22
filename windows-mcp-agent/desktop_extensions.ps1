# Built into the agent. This extends the guarded UIA worker; no update batch is
# prepared/executed for a desktop operation.
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class JarvisWindowNative {
 public delegate bool EnumCallback(IntPtr h,IntPtr l);
 [DllImport("user32.dll")]static extern bool EnumWindows(EnumCallback cb,IntPtr l);
 [DllImport("user32.dll")]static extern bool EnumChildWindows(IntPtr h,EnumCallback cb,IntPtr l);
 [DllImport("user32.dll")]public static extern bool IsWindow(IntPtr h);
 [DllImport("user32.dll")]public static extern IntPtr GetWindow(IntPtr h,uint cmd);
 [DllImport("user32.dll")]public static extern uint GetWindowThreadProcessId(IntPtr h,out uint pid);
 [DllImport("user32.dll")]public static extern bool IsWindowVisible(IntPtr h);
 [DllImport("user32.dll")]public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")]static extern bool ShowWindowAsync(IntPtr h,int cmd);
 [DllImport("user32.dll")]public static extern int GetDlgCtrlID(IntPtr h);
 [DllImport("user32.dll")]static extern bool AttachThreadInput(uint a,uint b,bool attach);
 [DllImport("kernel32.dll")]static extern uint GetCurrentThreadId();
 [DllImport("user32.dll")]static extern IntPtr SetFocus(IntPtr h);
 [DllImport("user32.dll")]static extern IntPtr GetFocus();
 [DllImport("user32.dll")]static extern short GetAsyncKeyState(int key);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetClassName(IntPtr h,StringBuilder b,int n);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetWindowText(IntPtr h,StringBuilder b,int n);
 [DllImport("user32.dll")]public static extern bool PostMessage(IntPtr h,uint msg,IntPtr w,IntPtr l);
 [StructLayout(LayoutKind.Sequential)]public struct K {public ushort vk,scan;public uint flags,time;public UIntPtr extra;}
 [StructLayout(LayoutKind.Sequential)]public struct M {public int x,y;public uint data,flags,time;public UIntPtr extra;}
 [StructLayout(LayoutKind.Explicit)]public struct U {[FieldOffset(0)]public K key;[FieldOffset(0)]public M mouse;}
 [StructLayout(LayoutKind.Sequential)]public struct I {public uint type;public U data;}
 [DllImport("user32.dll",SetLastError=true)]static extern uint SendInput(uint n,I[] inputs,int size);
 [StructLayout(LayoutKind.Sequential)]public struct P {public int X,Y;}
 [StructLayout(LayoutKind.Sequential)]public struct R {public int Left,Top,Right,Bottom;}
 [DllImport("user32.dll")]static extern bool GetWindowRect(IntPtr h,out R rect);
 [DllImport("user32.dll")]static extern IntPtr WindowFromPoint(P p);
 [DllImport("user32.dll")]static extern IntPtr GetAncestor(IntPtr h,uint flags);
 [DllImport("user32.dll")]static extern bool SetCursorPos(int x,int y);
 [DllImport("user32.dll")]static extern int GetSystemMetrics(int which);
 static I MouseSignal(uint flags,uint data=0){return new I{type=0,data=new U{mouse=new M{flags=flags,data=data}}};}

 // Atomic drag: mouse button down, motion, release, all in this one request.
 // A button must never stay down across separate MCP requests or retries.
 static void CheckDragPoint(IntPtr root,int x,int y){
  IntPtr hit=WindowFromPoint(new P{X=x,Y=y});
  if(hit==IntPtr.Zero||GetAncestor(hit,2)!=root)throw new Exception("drag_point_occluded_or_window_changed");
 }
 static void DragInRoot(IntPtr root,int x,int y,int endX,int endY){
  IdleModifiers();
  if((GetAsyncKeyState(1)&0x8000)!=0)throw new Exception("left_mouse_button_already_pressed");
  if(GetForegroundWindow()!=root)throw new Exception("foreground_changed");
  CheckDragPoint(root,x,y);CheckDragPoint(root,endX,endY);
  if(!SetCursorPos(x,y))throw new Exception("mouse_position_failed");
  System.Threading.Thread.Sleep(35);
  bool downAttempted=false;
  try {
   downAttempted=true;
   Send(new List<I>{MouseSignal(0x0002)});
   System.Threading.Thread.Sleep(35);
   for(int step=1;step<=16;step++){
    if(GetForegroundWindow()!=root)throw new Exception("foreground_changed_during_drag");
    int nextX=x+(int)(((long)endX-x)*step/16);
    int nextY=y+(int)(((long)endY-y)*step/16);
    CheckDragPoint(root,nextX,nextY);
    if(!SetCursorPos(nextX,nextY))throw new Exception("mouse_position_failed_during_drag");
    System.Threading.Thread.Sleep(20);
   }
  } finally {
   // Even if DOWN dispatch was uncertain, release before this request ends.
   if(downAttempted){
    I[] release=new I[]{MouseSignal(0x0004)};
    if(SendInput(1,release,Marshal.SizeOf(typeof(I)))!=1)throw new Exception("mouse_release_uncertain");
   }
  }
 }
 public static void MouseDrag(IntPtr target,int x,int y,int endX,int endY){
  R rect;
  if(!GetWindowRect(target,out rect)||x<rect.Left||x>=rect.Right||y<rect.Top||y>=rect.Bottom||
     endX<rect.Left||endX>=rect.Right||endY<rect.Top||endY>=rect.Bottom)
   throw new Exception("drag_point_outside_window");
  DragInRoot(target,x,y,endX,endY);
 }
 public static void MouseScreenDrag(int x,int y,int endX,int endY){
  int left=GetSystemMetrics(76),top=GetSystemMetrics(77);
  int width=GetSystemMetrics(78),height=GetSystemMetrics(79);
  if(width<1||height<1||x<left||x>=left+width||y<top||y>=top+height||
     endX<left||endX>=left+width||endY<top||endY>=top+height)
   throw new Exception("drag_point_outside_desktop");
  IntPtr hit=WindowFromPoint(new P{X=x,Y=y});
  if(hit==IntPtr.Zero)throw new Exception("drag_point_without_visible_window");
  DragInRoot(GetAncestor(hit,2),x,y,endX,endY);
 }
 public static void Mouse(IntPtr target,int x,int y,string action,int wheelDelta){
  IdleModifiers();
  R r;if(!GetWindowRect(target,out r)||x<r.Left||y<r.Top||x>=r.Right||y>=r.Bottom)throw new Exception("point_outside_window");
  IntPtr hit=WindowFromPoint(new P{X=x,Y=y});
  if(hit==IntPtr.Zero||GetAncestor(hit,2)!=target)throw new Exception("point_occluded_or_window_changed");
  if(!SetCursorPos(x,y))throw new Exception("mouse_position_failed");
  if(action=="move")return;
  var a=new List<I>();
  if(action=="wheel"){if(wheelDelta==0||wheelDelta<-1200||wheelDelta>1200||wheelDelta%120!=0)throw new Exception("invalid_wheel_delta");a.Add(MouseSignal(0x0800,unchecked((uint)wheelDelta)));}
  else{
   uint down=action=="right_click"?0x0008u:0x0002u,up=action=="right_click"?0x0010u:0x0004u;
   if(action!="click"&&action!="double_click"&&action!="right_click")throw new Exception("invalid_mouse_action");
   a.Add(MouseSignal(down));a.Add(MouseSignal(up));
   if(action=="double_click"){a.Add(MouseSignal(down));a.Add(MouseSignal(up));}
  }
  Send(a);
 }
 // Screen capture maps image pixels to physical x/y before invoking this
 // method. Unlike Mouse(window,...), it intentionally permits visible
 // taskbar/preview elements without a foreground-window snapshot.
 public static void MouseScreen(int x,int y,string action,int wheelDelta){
  IdleModifiers();
  int left=GetSystemMetrics(76),top=GetSystemMetrics(77);
  int width=GetSystemMetrics(78),height=GetSystemMetrics(79);
  if(width<1||height<1||x<left||x>=left+width||y<top||y>=top+height)
   throw new Exception("point_outside_desktop");
  if(WindowFromPoint(new P{X=x,Y=y})==IntPtr.Zero)throw new Exception("point_without_visible_window");
  if(!SetCursorPos(x,y))throw new Exception("mouse_position_failed");
  if(action=="move")return;
  var a=new List<I>();
  if(action=="wheel"){
   if(wheelDelta==0||wheelDelta<-1200||wheelDelta>1200||wheelDelta%120!=0)throw new Exception("invalid_wheel_delta");
   a.Add(MouseSignal(0x0800,unchecked((uint)wheelDelta)));
  }else{
   if(action!="click"&&action!="double_click"&&action!="right_click")throw new Exception("invalid_mouse_action");
   uint down=action=="right_click"?0x0008u:0x0002u;
   uint up=action=="right_click"?0x0010u:0x0004u;
   a.Add(MouseSignal(down));a.Add(MouseSignal(up));
   if(action=="double_click"){a.Add(MouseSignal(down));a.Add(MouseSignal(up));}
  }
  Send(a);
 }
 public static IntPtr[] Windows(){var list=new List<IntPtr>();EnumWindows((h,l)=>{if(IsWindowVisible(h))list.Add(h);return list.Count<256;},IntPtr.Zero);return list.ToArray();}
 public static string Class(IntPtr h){var b=new StringBuilder(128);GetClassName(h,b,128);return b.ToString();}
 public static string Title(IntPtr h){var b=new StringBuilder(512);GetWindowText(h,b,512);return b.ToString();}
 public static int Pid(IntPtr h){uint p;GetWindowThreadProcessId(h,out p);return (int)p;}
 public static IntPtr FindChild(IntPtr h,int id,string cls){IntPtr found=IntPtr.Zero;int count=0;EnumChildWindows(h,(c,l)=>{if(GetDlgCtrlID(c)==id&&Class(c)==cls){found=c;count++;}return true;},IntPtr.Zero);return count==1?found:IntPtr.Zero;}
 public static bool OwnedBy(IntPtr h,IntPtr owner){for(int n=0;n<16&&h!=IntPtr.Zero;n++){h=GetWindow(h,4);if(h==owner)return true;}return false;}
 public static bool Activate(IntPtr h){ShowWindowAsync(h,9);SetForegroundWindow(h);return GetForegroundWindow()==h;}
 public static void Focus(IntPtr h){uint p;uint target=GetWindowThreadProcessId(h,out p),self=GetCurrentThreadId();if(!AttachThreadInput(self,target,true))throw new Exception("focus_failed");try{SetFocus(h);if(GetFocus()!=h)throw new Exception("focus_failed");}finally{AttachThreadInput(self,target,false);}}
 public static void IdleModifiers(){foreach(int k in new[]{16,17,18,91,92})if((GetAsyncKeyState(k)&0x8000)!=0)throw new Exception("modifier_pressed");}
 static I Key(ushort vk,ushort scan,uint flags){return new I{type=1,data=new U{key=new K{vk=vk,scan=scan,flags=flags}}};}
 static void Send(List<I> a){if(SendInput((uint)a.Count,a.ToArray(),Marshal.SizeOf(typeof(I)))!=a.Count)throw new Exception("keyboard_outcome_uncertain");}
 public static void TypeText(string text){IdleModifiers();var a=new List<I>();foreach(char c in text){a.Add(Key(0,c,4));a.Add(Key(0,c,6));}if(a.Count>0)Send(a);}
 public static void Chord(string[] names){
  IdleModifiers();var mods=new List<ushort>();ushort key=0;
  foreach(string raw in names){string s=raw.ToUpperInvariant();ushort v;
   if(s=="CTRL"||s=="CONTROL")v=17;else if(s=="SHIFT")v=16;else if(s=="ALT")v=18;
   else {if(key!=0)throw new Exception("invalid_key_chord");if(s.Length==1&&((s[0]>='A'&&s[0]<='Z')||(s[0]>='0'&&s[0]<='9')))v=s[0];
    else if(s=="ENTER")v=13;else if(s=="TAB")v=9;else if(s=="ESC"||s=="ESCAPE")v=27;else if(s=="BACKSPACE")v=8;else if(s=="DELETE")v=46;else if(s=="HOME")v=36;else if(s=="END")v=35;else if(s=="LEFT")v=37;else if(s=="UP")v=38;else if(s=="RIGHT")v=39;else if(s=="DOWN")v=40;
    else {int n;if(s.StartsWith("F")&&Int32.TryParse(s.Substring(1),out n)&&n>=1&&n<=24)v=(ushort)(111+n);else throw new Exception("invalid_key_chord");}key=v;continue;}
   if(mods.Contains(v))throw new Exception("invalid_key_chord");mods.Add(v);
  }
  if(key==0)throw new Exception("invalid_key_chord");var a=new List<I>();foreach(ushort m in mods)a.Add(Key(m,0,0));a.Add(Key(key,0,0));a.Add(Key(key,0,2));for(int n=mods.Count-1;n>=0;n--)a.Add(Key(mods[n],0,2));
  try{Send(a);}catch{var release=new List<I>();foreach(ushort m in mods)release.Add(Key(m,0,2));SendInput((uint)release.Count,release.ToArray(),Marshal.SizeOf(typeof(I)));throw;}
 }
}
'@
$script:TargetWindow=$null
function ValidateWindow($identity){
 if(![JarvisDesktopNative]::DefaultInputDesktop()){Refuse 'desktop_unavailable_or_locked'}
 $h=[IntPtr][long]$identity.hwnd
 if(![JarvisWindowNative]::IsWindow($h) -or [JarvisDesktopNative]::GetAncestor($h,2).ToInt64() -ne $h.ToInt64()){Refuse 'window_changed'}
 if([JarvisWindowNative]::Pid($h) -ne [int]$identity.pid){Refuse 'window_changed'}
 $p=Get-Process -Id $identity.pid
 if($p.StartTime.ToUniversalTime().Ticks.ToString() -cne [string]$identity.start_ticks){Refuse 'window_changed'}
 if(!$p.SessionId -or $p.SessionId -ne (Get-Process -Id $PID).SessionId){Refuse 'wrong_interactive_session'}
 return $h
}
function RequireForeground($h){
 if([JarvisWindowNative]::GetForegroundWindow() -ne $h){Refuse 'foreground_changed'}
 if(![JarvisDesktopNative]::DefaultInputDesktop()){Refuse 'desktop_unavailable_or_locked'}
}
function FreshResponse($data){
 if($req.return_snapshot){
  $script:TargetWindow=$null
  try{$w=WindowState;$expectedPid=if($req.window){$req.window.pid}else{$req.target_window.pid};if($w.identity.pid -eq $expectedPid){$walk=Walk $w.element;$data.window=$w.identity;$data.elements=@($walk.items|ForEach-Object{@{id=$_.id;identity=$_.identity;name=$_.name;type=$_.type;automation_id=$_.automation_id;patterns=@($_.patterns);native_handle=$_.identity.hwnd;bounds=$_.identity.bounds}});$data.truncated=$walk.truncated}}catch{}
 }
 [Console]::Write((Json $data))
}
function FileHash($path){return (Get-FileHash -LiteralPath $path -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()}
function DirectOperation($req){
 if($req.operation -eq 'window_list'){
  if(![JarvisDesktopNative]::DefaultInputDesktop()){Refuse 'desktop_unavailable_or_locked'}
  $windows=@();$session=(Get-Process -Id $PID).SessionId
  foreach($h in [JarvisWindowNative]::Windows()){
   try{$p=Get-Process -Id ([JarvisWindowNative]::Pid($h));if($p.SessionId -ne $session -or !$p.SessionId){continue};$title=[JarvisWindowNative]::Title($h);if(!$title){continue};$windows+=@{hwnd=$h.ToInt64();pid=$p.Id;start_ticks=$p.StartTime.ToUniversalTime().Ticks.ToString();title=$title;minimized=[JarvisDesktopNative]::IsIconic($h)}}catch{}
  }
  [Console]::Write((Json @{ok=$true;windows=$windows}));return $true
 }
 if($req.operation -eq 'window_focus'){
  $h=ValidateWindow $req.target_window
  $ok=[JarvisWindowNative]::Activate($h);Start-Sleep -Milliseconds 100;RequireForeground $h
  FreshResponse @{ok=$true;dispatched=$true;verified=$true;data=@{status='focused'}};return $true
 }
 if($req.operation -eq 'window_close'){
  $h=ValidateWindow $req.target_window
  if(![JarvisWindowNative]::PostMessage($h,16,[IntPtr]::Zero,[IntPtr]::Zero)){Refuse 'close_dispatch_failed'}
  $state='pending';$prompt=$null
  for($i=0;$i -lt 30;$i++){
   Start-Sleep -Milliseconds 100
   if(![JarvisWindowNative]::IsWindow($h)){$state='closed';break}
   $fg=[JarvisWindowNative]::GetForegroundWindow()
   if($fg -ne $h -and [JarvisWindowNative]::Pid($fg) -eq $req.target_window.pid -and [JarvisWindowNative]::OwnedBy($fg,$h)){$state='needs_input';$prompt=[JarvisWindowNative]::Title($fg);break}
  }
  [Console]::Write((Json @{ok=$true;dispatched=$true;verified=($state -eq 'closed');data=@{status=$state;prompt_title=$prompt;forced=$false}}));return $true
 }
 if($req.operation -eq 'ui_mouse'){
  $h=ValidateWindow $req.window;RequireForeground $h
  $w=WindowState;if((Json $w.identity) -cne (Json $req.window)){Refuse 'window_changed'}
  if($req.mouse_action -eq 'drag'){
   [JarvisWindowNative]::MouseDrag($h,[int]$req.x,[int]$req.y,[int]$req.end_x,[int]$req.end_y)
  }else{
   [JarvisWindowNative]::Mouse($h,[int]$req.x,[int]$req.y,[string]$req.mouse_action,[int]$req.wheel_delta)
  }
  Start-Sleep -Milliseconds 120
  FreshResponse @{ok=$true;dispatched=$true;verified=$false;data=@{status=$(if($req.mouse_action -eq 'move'){'mouse_move_dispatched'}else{'mouse_dispatched'});mouse_action=[string]$req.mouse_action}};return $true
 }
 if($req.operation -eq 'ui_mouse_screen'){
  if(-not [Environment]::UserInteractive -or -not [JarvisDesktopNative]::DefaultInputDesktop() -or [JarvisWindowNative]::GetForegroundWindow() -eq [IntPtr]::Zero){Refuse 'desktop_unavailable_or_locked'}
  if($req.mouse_action -eq 'drag'){
   [JarvisWindowNative]::MouseScreenDrag([int]$req.x,[int]$req.y,[int]$req.end_x,[int]$req.end_y)
  }else{
   [JarvisWindowNative]::MouseScreen([int]$req.x,[int]$req.y,[string]$req.mouse_action,[int]$req.wheel_delta)
  }
  [Console]::Write((Json @{ok=$true;dispatched=$true;verified=$false;data=@{status='mouse_screen_dispatched';mouse_action=[string]$req.mouse_action;x=[int]$req.x;y=[int]$req.y}}));return $true
 }
 if($req.operation -eq 'ui_keys'){
  $h=ValidateWindow $req.window;RequireForeground $h
  $w=WindowState;if((Json $w.identity) -cne (Json $req.window)){Refuse 'window_changed'}
  [JarvisWindowNative]::Chord([string[]]$req.keys);Start-Sleep -Milliseconds 150
  FreshResponse @{ok=$true;dispatched=$true;verified=$false;data=@{status='keys_dispatched'}};return $true
 }
 if($req.operation -eq 'ui_file_dialog'){
  $h=ValidateWindow $req.target_window
  $path=[string]$req.path;$save=($req.dialog_mode -eq 'save');$exists=[IO.File]::Exists($path)
  if($save -and $exists){if(!$req.overwrite){Refuse 'destination_exists'};if(!$req.expected_sha256 -or (FileHash $path) -cne [string]$req.expected_sha256.ToLowerInvariant()){Refuse 'destination_changed'}}
  if(!$save -and !$exists){Refuse 'source_file_missing'}
  if(![JarvisWindowNative]::Activate($h)){Refuse 'focus_failed'};RequireForeground $h
  if($save){[JarvisWindowNative]::Chord(@('CTRL','SHIFT','S'))}else{[JarvisWindowNative]::Chord(@('CTRL','O'))}
  $dialog=[IntPtr]::Zero;$edit=[IntPtr]::Zero;$button=[IntPtr]::Zero
  for($i=0;$i -lt 50;$i++){
   Start-Sleep -Milliseconds 100;$candidate=[JarvisWindowNative]::GetForegroundWindow()
   if($candidate -eq $h -or [JarvisWindowNative]::Pid($candidate) -ne $req.target_window.pid -or ![JarvisWindowNative]::OwnedBy($candidate,$h)){continue}
   $edit=[JarvisWindowNative]::FindChild($candidate,1001,'Edit');$button=[JarvisWindowNative]::FindChild($candidate,1,'Button')
   if($edit -ne [IntPtr]::Zero -and $button -ne [IntPtr]::Zero){$dialog=$candidate;break}
  }
  if($dialog -eq [IntPtr]::Zero){Refuse 'standard_file_dialog_not_found'}
  RequireForeground $dialog;[JarvisWindowNative]::Focus($edit);RequireForeground $dialog
  [JarvisWindowNative]::Chord(@('CTRL','A'));Start-Sleep -Milliseconds 100;RequireForeground $dialog
  [JarvisWindowNative]::TypeText($path)
  $matched=$false
  for($j=0;$j -lt 40;$j++){if([JarvisDesktopNative]::Text($edit) -ceq $path){$matched=$true;break};Start-Sleep -Milliseconds 50}
  if(!$matched){Refuse 'filename_verification_failed'}
  if($save -and [IO.File]::Exists($path)){
   if(!$req.overwrite -or !$req.expected_sha256 -or (FileHash $path) -cne [string]$req.expected_sha256.ToLowerInvariant()){Refuse 'destination_changed'}
  }
  $clicked=[DateTime]::UtcNow
  RequireForeground $dialog
  [JarvisWindowNative]::Focus($edit);RequireForeground $dialog
  [JarvisWindowNative]::Chord(@('ENTER'))
  $approvedOverwrite=$false;$complete=$false
  for($i=0;$i -lt 100;$i++){
   Start-Sleep -Milliseconds 100
   $fg=[JarvisWindowNative]::GetForegroundWindow()
   if($save -and $exists -and $req.overwrite -and !$approvedOverwrite -and $fg -ne $dialog -and [JarvisWindowNative]::Pid($fg) -eq $req.target_window.pid -and [JarvisWindowNative]::OwnedBy($fg,$dialog)){
    $yes=[JarvisWindowNative]::FindChild($fg,6,'Button')
    if($yes -ne [IntPtr]::Zero -and (FileHash $path) -ceq [string]$req.expected_sha256.ToLowerInvariant()){RequireForeground $fg;[void][JarvisWindowNative]::PostMessage($yes,245,[IntPtr]::Zero,[IntPtr]::Zero);$approvedOverwrite=$true}
   }
   if([JarvisWindowNative]::IsWindow($dialog)){continue}
   if(!$save){$complete=$true;break}
   if([IO.File]::Exists($path)){
    try{$info=Get-Item -LiteralPath $path;if($exists -and $info.LastWriteTimeUtc -lt $clicked){continue};if($info.Length -gt 1048576){$complete=$true;break};if($null -ne $req.expected_text -and [IO.File]::ReadAllText($path) -cne [string]$req.expected_text){continue};$complete=$true;break}catch{}
   }
  }
  if(!$complete){Refuse 'file_dialog_outcome_uncertain'}
  $status=if($save){'saved'}else{'opened'};$data=@{status=$status;path=$path;method='application_file_dialog'}
  if($save){$info=Get-Item -LiteralPath $path;$data.bytes=$info.Length;$data.sha256=FileHash $path;if($null -ne $req.expected_text){if($info.Length -gt 1048576){Refuse 'file_verification_limit'};$data.text_matches=([IO.File]::ReadAllText($path) -ceq [string]$req.expected_text)}}
  FreshResponse @{ok=$true;dispatched=$true;verified=$save;data=$data};return $true
 }
 return $false
}

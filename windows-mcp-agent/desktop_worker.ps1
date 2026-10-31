$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
[Console]::InputEncoding=New-Object Text.UTF8Encoding($false)
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
try{
 Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes,WindowsBase
 Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class JarvisDesktopNative {
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
 [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
 [DllImport("user32.dll")] static extern IntPtr OpenInputDesktop(uint a,bool b,uint c);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern bool GetUserObjectInformation(IntPtr h,int n,StringBuilder b,int len,out int needed);
 [DllImport("user32.dll")] static extern bool CloseDesktop(IntPtr h);
 public static bool DefaultInputDesktop(){
  IntPtr h=OpenInputDesktop(0,false,1);
  if(h==IntPtr.Zero)return false;
  try{int size;var b=new StringBuilder(256);return GetUserObjectInformation(h,2,b,512,out size)&&b.ToString()=="Default";}finally{CloseDesktop(h);}
 }
}
'@
 function Refuse($code){throw $code}
 function Rect($r){return @([double]$r.X,[double]$r.Y,[double]$r.Width,[double]$r.Height)}
 function Json($v){return ConvertTo-Json -InputObject $v -Depth 12 -Compress}
 function WindowState{
  if(![Environment]::UserInteractive -or ![JarvisDesktopNative]::DefaultInputDesktop()){Refuse 'desktop_unavailable_or_locked'}
  $h=[JarvisDesktopNative]::GetForegroundWindow()
  if($h -eq [IntPtr]::Zero -or ![JarvisDesktopNative]::IsWindowVisible($h) -or [JarvisDesktopNative]::IsIconic($h)){Refuse 'no_visible_foreground_window'}
  $w=[Windows.Automation.AutomationElement]::FromHandle($h)
  $c=$w.Current
  $proc=Get-Process -Id $c.ProcessId
  $identity=[ordered]@{hwnd=$h.ToInt64();pid=$c.ProcessId;start_ticks=$proc.StartTime.ToUniversalTime().Ticks;runtime=($w.GetRuntimeId() -join '.');bounds=(Rect $c.BoundingRectangle);title=$c.Name}
  return @{element=$w;identity=$identity}
 }
 function ElementState($e){
  $c=$e.Current
  $rid=$e.GetRuntimeId() -join '.'
  $patterns=@()
  $valueHash=""
  foreach($entry in @(
   @('invoke',[Windows.Automation.InvokePattern]::Pattern),
   @('set_text',[Windows.Automation.ValuePattern]::Pattern),
   @('toggle',[Windows.Automation.TogglePattern]::Pattern),
   @('select',[Windows.Automation.SelectionItemPattern]::Pattern))){
   [object]$obj=$null
   if($e.TryGetCurrentPattern($entry[1],[ref]$obj)){
    $patterns+= $entry[0]
    if($entry[0] -eq 'set_text' -and !$c.IsPassword){
     $value=[string]$obj.Current.Value
     if($value.Length -gt 1048576){Refuse 'editable_content_too_large'}
     $sha=[Security.Cryptography.SHA256]::Create()
     try{$valueHash=[Convert]::ToBase64String($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($value)))}finally{$sha.Dispose()}
    }
   }
  }
  $name=if($c.IsPassword -or $c.ControlType -eq [Windows.Automation.ControlType]::Edit -or $c.ControlType -eq [Windows.Automation.ControlType]::Document){''}else{$c.Name}
  if($name.Length -gt 120){$name=$name.Substring(0,120)}
  $identity=[ordered]@{runtime=$rid;pid=$c.ProcessId;hwnd=$c.NativeWindowHandle;class=$c.ClassName;automation_id=$c.AutomationId;type=$c.ControlType.ProgrammaticName;name=$name;bounds=(Rect $c.BoundingRectangle);enabled=$c.IsEnabled;offscreen=$c.IsOffscreen;password=$c.IsPassword;value_hash=$valueHash;patterns=@($patterns)}
  return @{element=$e;identity=$identity;id=$rid;name=$name;type=$c.ControlType.ProgrammaticName;automation_id=$c.AutomationId;patterns=@($patterns)}
 }
 function Walk($root){
  $list=New-Object 'System.Collections.Generic.List[object]'
  $stack=New-Object 'System.Collections.Generic.Stack[object]'
  $stack.Push($root);$seen=0;$cut=$false
  $walker=[Windows.Automation.TreeWalker]::ControlViewWalker
  while($stack.Count -gt 0){
   if($seen -ge 512 -or $list.Count -ge 64){$cut=$true;break}
   $e=$stack.Pop();$seen++
   $s=ElementState $e
   if($s.identity.pid -ne $root.Current.ProcessId){continue}
   if($s.identity.password){continue}
   if($s.patterns.Count -gt 0 -and $s.id){$list.Add($s)}
   $kind=$e.Current.ControlType
   if($kind -eq [Windows.Automation.ControlType]::Edit -or $kind -eq [Windows.Automation.ControlType]::Document){continue}
   $children=@();$child=$walker.GetFirstChild($e)
   while($null -ne $child){$children+=,$child;if($children.Count -ge 512){$cut=$true;break};$child=$walker.GetNextSibling($child)}
   for($i=$children.Count-1;$i -ge 0;$i--){$stack.Push($children[$i])}
  }
  return @{items=$list.ToArray();truncated=$cut}
 }
 function CheckedTarget($req){
  $w=WindowState
  if((Json $w.identity) -cne (Json $req.window)){Refuse 'window_changed'}
  $walk=Walk $w.element
  $matches=@($walk.items|Where-Object {$_.id -ceq $req.target.runtime})
  if($matches.Count -ne 1){Refuse 'target_missing_or_ambiguous'}
  $s=$matches[0]
  if((Json $s.identity) -cne (Json $req.target)){Refuse 'target_changed'}
  if(!$s.identity.enabled -or $s.identity.offscreen -or $s.identity.password){Refuse 'target_not_actionable'}
  $bounds=$s.identity.bounds
  if($bounds[2] -le 0 -or $bounds[3] -le 0){Refuse 'empty_target_bounds'}
  if($s.patterns -notcontains $req.action){Refuse 'pattern_not_supported'}
  return $s
 }
 $req=[Console]::In.ReadToEnd()|ConvertFrom-Json
 if($req.operation -eq 'desktop_inspect'){
  $w=WindowState;$walk=Walk $w.element
  $again=WindowState
  if((Json $w.identity) -cne (Json $again.identity)){Refuse 'window_changed_during_inspect'}
  $elements=@($walk.items|ForEach-Object {@{id=$_.id;identity=$_.identity;name=$_.name;type=$_.type;automation_id=$_.automation_id;patterns=@($_.patterns)}})
  [Console]::Write((Json @{ok=$true;window=$w.identity;elements=$elements;truncated=$walk.truncated}))
 }elseif($req.operation -eq 'desktop_action'){
  $null=CheckedTarget $req
  Start-Sleep -Milliseconds 250
  $s=CheckedTarget $req
  $e=$s.element;$verified=$false
  switch($req.action){
   'set_text' {
    if($s.type -ne 'ControlType.Edit'){Refuse 'text_requires_edit_control'}
    $v=[Windows.Automation.ValuePattern]$e.GetCurrentPattern([Windows.Automation.ValuePattern]::Pattern)
    if($v.Current.IsReadOnly){Refuse 'read_only_target'}
    # Last foreground check. The call addresses this UIA object, never a screen point or global keyboard.
    $null=CheckedTarget $req
    $v.SetValue([string]$req.text)
    $verified=($v.Current.Value -ceq [string]$req.text)
   }
   'invoke' {
    $v=[Windows.Automation.InvokePattern]$e.GetCurrentPattern([Windows.Automation.InvokePattern]::Pattern)
    $null=CheckedTarget $req
    $v.Invoke()
   }
   'select' {
    $v=[Windows.Automation.SelectionItemPattern]$e.GetCurrentPattern([Windows.Automation.SelectionItemPattern]::Pattern)
    $null=CheckedTarget $req
    $v.Select();$verified=$v.Current.IsSelected
   }
   'toggle' {
    $v=[Windows.Automation.TogglePattern]$e.GetCurrentPattern([Windows.Automation.TogglePattern]::Pattern)
    $before=$v.Current.ToggleState
    $null=CheckedTarget $req
    $v.Toggle();$verified=($v.Current.ToggleState -ne $before)
   }
   default {Refuse 'unsupported_action'}
  }
  [Console]::Write((Json @{ok=$true;dispatched=$true;verified=$verified;action=$req.action}))
 }else{Refuse 'unsupported_operation'}
}catch{
 # Never include provider exceptions (which can contain entered text) in diagnostics.
 $allowed=@('desktop_unavailable_or_locked','no_visible_foreground_window','window_changed','target_missing_or_ambiguous','target_changed','target_not_actionable','empty_target_bounds','pattern_not_supported','window_changed_during_inspect','text_requires_edit_control','read_only_target','unsupported_action','unsupported_operation','editable_content_too_large')
 $code=$_.Exception.Message
 if($allowed -notcontains $code){$code='provider_error_or_uncertain_outcome'}
 [Console]::Write((ConvertTo-Json -Compress @{ok=$false;error=$code}))
}

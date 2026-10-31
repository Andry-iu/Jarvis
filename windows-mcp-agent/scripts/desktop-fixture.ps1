param([Parameter(Mandatory=$true)][string]$Report)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms,System.Drawing
Add-Type -TypeDefinition @'
using System;using System.Runtime.InteropServices;
public static class FixtureForeground {
 [DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")]public static extern bool ShowWindow(IntPtr h,int cmd);
 [DllImport("user32.dll")]public static extern IntPtr GetForegroundWindow();
}
'@
$f=New-Object Windows.Forms.Form
$f.Text='Jarvis isolated desktop validation'
$f.Size=New-Object Drawing.Size(580,360)
$f.StartPosition='Manual'
$f.Location=New-Object Drawing.Point(100,100)
$script:Good=0;$script:Decoy=0;$script:Last=''
$inputBox=New-Object Windows.Forms.TextBox
$inputBox.Name='fixtureInput';$inputBox.AccessibleName='Fixture input';$inputBox.SetBounds(25,25,400,30)
$readonly=New-Object Windows.Forms.TextBox
$readonly.Name='fixtureReadOnly';$readonly.ReadOnly=$true;$readonly.Text='READONLY';$readonly.SetBounds(25,65,400,30)
$password=New-Object Windows.Forms.TextBox
$password.Name='fixturePassword';$password.UseSystemPasswordChar=$true;$password.Text='DUMMY';$password.SetBounds(25,105,400,30)
$good=New-Object Windows.Forms.Button
$good.Name='fixtureGood';$good.AccessibleName='Conferma';$good.Text='Conferma';$good.SetBounds(25,155,130,35)
$decoy=New-Object Windows.Forms.Button
$decoy.Name='fixtureDecoy';$decoy.AccessibleName='Conferma';$decoy.Text='Conferma';$decoy.SetBounds(185,155,130,35)
$disabled=New-Object Windows.Forms.Button
$disabled.Name='fixtureDisabled';$disabled.Text='Disabled';$disabled.Enabled=$false;$disabled.SetBounds(345,155,130,35)
$check=New-Object Windows.Forms.CheckBox
$check.Name='fixtureToggle';$check.Text='Fixture toggle';$check.SetBounds(25,210,200,30)
$other=New-Object Windows.Forms.Form
$other.Text=$f.Text;$other.Size=New-Object Drawing.Size(300,200)
$otherInput=New-Object Windows.Forms.TextBox
$otherInput.Name='fixtureInput';$other.Controls.Add($otherInput)
function Save-Report{
 $data=@{good=$script:Good;decoy=$script:Decoy;text=$inputBox.Text;last=$script:Last;hwnd=$f.Handle.ToInt64();other_hwnd=$other.Handle.ToInt64();toggle=$check.Checked;handles=@{fixtureInput=$inputBox.Handle.ToInt64();fixtureReadOnly=$readonly.Handle.ToInt64();fixturePassword=$password.Handle.ToInt64();fixtureGood=$good.Handle.ToInt64();fixtureDecoy=$decoy.Handle.ToInt64();fixtureDisabled=$disabled.Handle.ToInt64();fixtureToggle=$check.Handle.ToInt64()};foreground=[FixtureForeground]::GetForegroundWindow().ToInt64();activated=$script:Activated}
 $tmp=$Report+'.tmp'
 [IO.File]::WriteAllText($tmp,($data|ConvertTo-Json -Compress),(New-Object Text.UTF8Encoding $false))
 Move-Item $tmp $Report -Force
}
$good.add_Click({$script:Good++;Save-Report})
$decoy.add_Click({$script:Decoy++;Save-Report})
$f.Controls.AddRange(@($inputBox,$readonly,$password,$good,$decoy,$disabled,$check))
$timer=New-Object Windows.Forms.Timer
$timer.Interval=100
$timer.add_Tick({
 if(Test-Path ($Report+'.command')){
 try{
 $c=Get-Content ($Report+'.command') -Raw|ConvertFrom-Json
 Remove-Item ($Report+'.command') -Force
 switch($c.action){
 'move' {$f.Location=New-Object Drawing.Point([int]$c.x,[int]$c.y)}
 'other' {$other.Show();$other.Activate()}
 'main' {$other.Hide();$f.Activate()}
 'edit' {$inputBox.Text='USER_CHANGED'}
 'readonly' {$inputBox.ReadOnly=$true}
 'writable' {$inputBox.ReadOnly=$false}
 'disable' {$good.Enabled=$false}
 'enable' {$good.Enabled=$true}
 'movecontrol' {$good.Left+=40}
 'exit' {$f.Close()}
 }
 $script:Last=$c.id;Save-Report
 }catch{}
 }
})
$f.add_Shown({[void][FixtureForeground]::ShowWindow($f.Handle,9);$script:Activated=[FixtureForeground]::SetForegroundWindow($f.Handle);$f.Activate();Save-Report})
$timer.Start()
try{[void]$f.ShowDialog()}finally{$timer.Stop();$other.Dispose();$f.Dispose()}

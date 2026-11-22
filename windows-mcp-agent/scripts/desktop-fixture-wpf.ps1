param([Parameter(Mandatory=$true)][string]$Report)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName PresentationFramework,PresentationCore,WindowsBase
[xml]$xml=@'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Title="Jarvis isolated desktop validation" Width="580" Height="360" Left="100" Top="100">
 <Canvas>
  <TextBox x:Name="fixtureInput" Canvas.Left="25" Canvas.Top="25" Width="400" Height="25"/>
  <TextBox x:Name="fixtureReadOnly" Canvas.Left="25" Canvas.Top="65" Width="400" Height="25" IsReadOnly="True" Text="READONLY"/>
  <PasswordBox x:Name="fixturePassword" Canvas.Left="25" Canvas.Top="105" Width="400" Height="25"/>
  <Button x:Name="fixtureGood" Canvas.Left="25" Canvas.Top="155" Width="130" Height="35" Content="Conferma"/>
  <Button x:Name="fixtureDecoy" Canvas.Left="185" Canvas.Top="155" Width="130" Height="35" Content="Conferma"/>
  <Button x:Name="fixtureDisabled" Canvas.Left="345" Canvas.Top="155" Width="130" Height="35" Content="Disabled" IsEnabled="False"/>
  <CheckBox x:Name="fixtureToggle" Canvas.Left="25" Canvas.Top="210" Width="200" Height="30" Content="Fixture toggle"/>
 </Canvas>
</Window>
'@
$f=[Windows.Markup.XamlReader]::Load((New-Object Xml.XmlNodeReader $xml))
$inputBox=$f.FindName('fixtureInput')
$readonly=$f.FindName('fixtureReadOnly')
$password=$f.FindName('fixturePassword')
$good=$f.FindName('fixtureGood')
$decoy=$f.FindName('fixtureDecoy')
$disabled=$f.FindName('fixtureDisabled')
$check=$f.FindName('fixtureToggle')
foreach($c in @($inputBox,$readonly,$password,$good,$decoy,$disabled,$check)){[Windows.Automation.AutomationProperties]::SetAutomationId($c,$c.Name)}
$password.Password='DUMMY'
$other=New-Object Windows.Window
$other.Title=$f.Title;$other.Width=300;$other.Height=200
$other.Content=New-Object Windows.Controls.TextBox
$script:GoodHits=0;$script:DecoyHits=0;$script:Last=''
$interop=New-Object Windows.Interop.WindowInteropHelper -ArgumentList $f
$otherInterop=New-Object Windows.Interop.WindowInteropHelper -ArgumentList $other
$null=$interop.EnsureHandle();$null=$otherInterop.EnsureHandle()
function Save-Report{
 $data=@{good=$script:GoodHits;decoy=$script:DecoyHits;text=$inputBox.Text;last=$script:Last;hwnd=$interop.Handle.ToInt64();other_hwnd=$otherInterop.Handle.ToInt64();toggle=[bool]$check.IsChecked;handles=@{}}
 $tmp=$Report+'.tmp'
 [IO.File]::WriteAllText($tmp,($data|ConvertTo-Json -Compress),(New-Object Text.UTF8Encoding $false))
 Move-Item $tmp $Report -Force
}
$good.add_Click({$script:GoodHits++;Save-Report})
$decoy.add_Click({$script:DecoyHits++;Save-Report})
$timer=New-Object Windows.Threading.DispatcherTimer
$timer.Interval=[TimeSpan]::FromMilliseconds(100)
$timer.add_Tick({
 if(Test-Path ($Report+'.command')){
 try{
 $c=Get-Content ($Report+'.command') -Raw -Encoding UTF8|ConvertFrom-Json
 Remove-Item ($Report+'.command') -Force
 switch($c.action){
 'move' {$f.Left=[double]$c.x;$f.Top=[double]$c.y}
 'other' {$other.Show();$null=$other.Activate()}
 'main' {$other.Hide();$null=$f.Activate()}
 'edit' {$inputBox.Text='USER_CHANGED'}
 'readonly' {$inputBox.IsReadOnly=$true}
 'writable' {$inputBox.IsReadOnly=$false}
 'disable' {$good.IsEnabled=$false}
 'enable' {$good.IsEnabled=$true}
 'movecontrol' {[Windows.Controls.Canvas]::SetLeft($good,([Windows.Controls.Canvas]::GetLeft($good)+40))}
 'exit' {$f.Close()}
 }
 $script:Last=$c.id;Save-Report
 }catch{}
 }
})
$f.add_ContentRendered({$null=$f.Activate();Save-Report})
$timer.Start()
try{$null=$f.ShowDialog()}finally{$timer.Stop();$other.Close();$f.Close()}

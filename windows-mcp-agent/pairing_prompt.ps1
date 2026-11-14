$ErrorActionPreference='Stop'
$p=[Console]::In.ReadToEnd()|ConvertFrom-Json
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class PairingDesktop {
 [DllImport("user32.dll")] static extern IntPtr OpenInputDesktop(uint f,bool i,uint a);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern bool GetUserObjectInformation(IntPtr h,int n,System.Text.StringBuilder b,int l,out int r);
 [DllImport("user32.dll")] static extern bool CloseDesktop(IntPtr h);
 public static bool Ready() {
  IntPtr h=OpenInputDesktop(0,false,1);if(h==IntPtr.Zero)return false;
  try {var b=new System.Text.StringBuilder(256);int n;return GetUserObjectInformation(h,2,b,512,out n)&&b.ToString().Equals("Default",StringComparison.OrdinalIgnoreCase);}
  finally {CloseDesktop(h);}
 }
}
'@
if(-not [PairingDesktop]::Ready()){exit 2}
$form=New-Object Windows.Forms.Form
$form.Text='Jarvis - Autorizza collegamento'
$form.Size=New-Object Drawing.Size(620,440)
$form.StartPosition='CenterScreen';$form.FormBorderStyle='FixedDialog'
$form.MaximizeBox=$false;$form.MinimizeBox=$false;$form.TopMost=$true
$label=New-Object Windows.Forms.Label
$label.Location=New-Object Drawing.Point(22,20);$label.Size=New-Object Drawing.Size(565,200)
$label.Font=New-Object Drawing.Font('Segoe UI',11)
$label.UseMnemonic=$false
$label.Text=('Consenti il controllo di questo computer da '+$p.name+'?'+"`r`n`r`n"+'Indirizzo richiedente: '+$p.address+"`r`n"+'Identita: '+$p.machine_id+"`r`n`r`n"+'Inserisci il codice ricevuto in chat per questa richiesta. Il nome non e una prova di identita.')
$form.Controls.Add($label)
# Keep the expected code in memory only: never put it in a visible control.
function Normalize-PairCode([string]$value) {
 if($null -eq $value -or $value.Length -gt 128){return ''}
 $value=[regex]::Replace($value,'[- \t\r\n]','')
 if($value -cnotmatch '^[0-9a-fA-F]{12}$'){return ''}
 return $value.ToUpperInvariant()
}
$expected=Normalize-PairCode $p.code
if(-not $expected){$form.Dispose();exit 2}
$inputCode=New-Object Windows.Forms.TextBox
$inputCode.Location=New-Object Drawing.Point(22,225);$inputCode.Size=New-Object Drawing.Size(350,30)
$inputCode.Font=New-Object Drawing.Font('Consolas',14);$inputCode.MaxLength=32
$inputCode.AccessibleName='Codice ricevuto in chat';$inputCode.TabIndex=0
$form.Controls.Add($inputCode)
$countdown=New-Object Windows.Forms.Label
$countdown.Location=New-Object Drawing.Point(390,225);$countdown.Size=New-Object Drawing.Size(190,30)
$countdown.Font=New-Object Drawing.Font('Segoe UI',11);$countdown.UseMnemonic=$false
$countdown.AccessibleName='Tempo rimasto';$countdown.Text='Tempo rimasto: 1:50'
$form.Controls.Add($countdown)
$errorLabel=New-Object Windows.Forms.Label
$errorLabel.Location=New-Object Drawing.Point(22,270);$errorLabel.Size=New-Object Drawing.Size(560,40)
$errorLabel.ForeColor=[Drawing.Color]::DarkRed;$errorLabel.UseMnemonic=$false
$form.Controls.Add($errorLabel)
$yes=New-Object Windows.Forms.Button;$yes.Text='Consenti';$yes.Location=New-Object Drawing.Point(335,335);$yes.Size=New-Object Drawing.Size(115,35)
$yes.Enabled=$false;$yes.TabIndex=1
$no=New-Object Windows.Forms.Button;$no.Text='Rifiuta';$no.Location=New-Object Drawing.Point(465,335);$no.Size=New-Object Drawing.Size(115,35);$no.TabIndex=2
$script:enteredCode='';$script:attempts=0
$deadline=[DateTime]::UtcNow.AddSeconds(110)
$inputCode.Add_TextChanged({$yes.Enabled=((Normalize-PairCode $inputCode.Text) -ne '')})
$yes.Add_Click({
 if([DateTime]::UtcNow -ge $deadline -or -not [PairingDesktop]::Ready()){$form.Close();return}
 $candidate=Normalize-PairCode $inputCode.Text
 if($candidate -and $candidate -ceq $expected){$script:enteredCode=$candidate;$form.Close();return}
 $script:attempts++
 if($script:attempts -ge 3){$form.Close();return}
 $errorLabel.Text=('Codice non corretto. Tentativi rimasti: '+(3-$script:attempts)+'.')
 $inputCode.Clear();$inputCode.Focus()
})
$no.Add_Click({$form.Close()});$form.CancelButton=$no
$form.Controls.Add($yes);$form.Controls.Add($no)
$form.Add_Shown({$inputCode.Focus()})
$timer=New-Object Windows.Forms.Timer;$timer.Interval=1000
$timer.Add_Tick({
 $remaining=[Math]::Max(0,[int][Math]::Ceiling(($deadline-[DateTime]::UtcNow).TotalSeconds))
 $countdown.Text=('Tempo rimasto: {0}:{1:00}' -f [int][Math]::Floor($remaining/60),($remaining%60))
 if($remaining -le 20){$countdown.ForeColor=[Drawing.Color]::DarkRed}
 if($remaining -le 0 -or -not [PairingDesktop]::Ready()){$script:enteredCode='';$form.Close()}
})
try{$timer.Start();$null=$form.ShowDialog();if($script:enteredCode -and [DateTime]::UtcNow -lt $deadline -and [PairingDesktop]::Ready()){[Console]::Out.Write($script:enteredCode)}else{[Console]::Out.Write('DENY')}}
finally{$timer.Stop();$timer.Dispose();$form.Dispose()}

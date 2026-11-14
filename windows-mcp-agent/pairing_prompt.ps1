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
$form.Size=New-Object Drawing.Size(620,390)
$form.StartPosition='CenterScreen';$form.FormBorderStyle='FixedDialog'
$form.MaximizeBox=$false;$form.MinimizeBox=$false;$form.TopMost=$true
$label=New-Object Windows.Forms.Label
$label.Location=New-Object Drawing.Point(22,20);$label.Size=New-Object Drawing.Size(565,235)
$label.Font=New-Object Drawing.Font('Segoe UI',11)
$label.UseMnemonic=$false
$label.Text=('Consenti il controllo di questo computer da '+$p.name+'?'+"`r`n`r`n"+'Indirizzo richiedente: '+$p.address+"`r`n"+'Identita: '+$p.machine_id+"`r`n`r`n"+'Codice: '+$p.code+"`r`n`r`n"+'Consenti solo se hai richiesto il collegamento e il codice coincide con quello mostrato in chat. Il nome non e una prova di identita.')
$form.Controls.Add($label)
$yes=New-Object Windows.Forms.Button;$yes.Text='Consenti';$yes.Location=New-Object Drawing.Point(335,285);$yes.Size=New-Object Drawing.Size(115,35)
$no=New-Object Windows.Forms.Button;$no.Text='Rifiuta';$no.Location=New-Object Drawing.Point(465,285);$no.Size=New-Object Drawing.Size(115,35)
$script:allow=$false
$yes.Add_Click({if([PairingDesktop]::Ready()){$script:allow=$true};$form.Close()})
$no.Add_Click({$form.Close()});$form.CancelButton=$no
$form.Controls.Add($yes);$form.Controls.Add($no)
$form.Add_Shown({$no.Focus()})
$deadline=[DateTime]::UtcNow.AddSeconds(110)
$timer=New-Object Windows.Forms.Timer;$timer.Interval=1000
$timer.Add_Tick({if([DateTime]::UtcNow -ge $deadline -or -not [PairingDesktop]::Ready()){$script:allow=$false;$form.Close()}})
try{$timer.Start();$null=$form.ShowDialog();if($script:allow -and [DateTime]::UtcNow -lt $deadline){[Console]::Out.Write('ALLOW')}else{[Console]::Out.Write('DENY')}}
finally{$timer.Stop();$timer.Dispose();$form.Dispose()}

$ErrorActionPreference='Stop'
[Console]::InputEncoding=New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false)
$bitmap=$null;$small=$null;$graphics=$null;$scaled=$null;$stream=$null;$desktop=[IntPtr]::Zero
try {
 $p=[Console]::In.ReadToEnd()|ConvertFrom-Json
 Add-Type -AssemblyName System.Windows.Forms,System.Drawing
 Add-Type @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class CaptureDesktop {
 [DllImport("user32.dll")] public static extern IntPtr OpenInputDesktop(uint f,bool inherit,uint access);
 [DllImport("user32.dll")] public static extern bool CloseDesktop(IntPtr h);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern bool GetUserObjectInformation(IntPtr h,int i,StringBuilder b,uint l,out uint needed);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
'@
 [CaptureDesktop]::SetProcessDPIAware()|Out-Null
 $desktop=[CaptureDesktop]::OpenInputDesktop(0,$false,1)
 if($desktop -eq [IntPtr]::Zero){throw 'interactive_desktop_unavailable'}
 $name=New-Object System.Text.StringBuilder 256;[uint32]$needed=0
 if(-not [CaptureDesktop]::GetUserObjectInformation($desktop,2,$name,512,[ref]$needed) -or $name.ToString() -ne 'Default' -or [CaptureDesktop]::GetForegroundWindow() -eq [IntPtr]::Zero){throw 'locked_or_secure_desktop'}
 $screens=@([System.Windows.Forms.Screen]::AllScreens|Sort-Object DeviceName)
 $monitors=@();for($i=0;$i -lt $screens.Count;$i++){$s=$screens[$i];$monitors+=@{index=$i+1;name=$s.DeviceName;primary=$s.Primary;x=$s.Bounds.X;y=$s.Bounds.Y;width=$s.Bounds.Width;height=$s.Bounds.Height}}
 $bounds=[System.Windows.Forms.SystemInformation]::VirtualScreen
 if([int]$p.monitor -gt 0){if([int]$p.monitor -gt $screens.Count){throw 'unknown_monitor'};$bounds=$screens[[int]$p.monitor-1].Bounds}
 if($p.region -and $p.region.Count -eq 4){$v=$p.region;$region=New-Object System.Drawing.Rectangle([int]$v[0],[int]$v[1],[int]$v[2],[int]$v[3]);if($region.Width -le 0 -or $region.Height -le 0 -or -not $bounds.Contains($region)){throw 'region_outside_selected_surface'};$bounds=$region}
 if($bounds.Width -gt 16384 -or $bounds.Height -gt 16384 -or [long]$bounds.Width*$bounds.Height -gt 40000000){throw 'capture_surface_too_large'}
 $bitmap=New-Object System.Drawing.Bitmap($bounds.Width,$bounds.Height)
 $graphics=[System.Drawing.Graphics]::FromImage($bitmap);$graphics.CopyFromScreen($bounds.Location,[System.Drawing.Point]::Empty,$bounds.Size);$graphics.Dispose();$graphics=$null
 $scale=[Math]::Min(1.0,[double]$p.max_width/$bounds.Width)
 $w=[Math]::Max(1,[int][Math]::Round($bounds.Width*$scale));$h=[Math]::Max(1,[int][Math]::Round($bounds.Height*$scale))
 $small=New-Object System.Drawing.Bitmap($w,$h);$scaled=[System.Drawing.Graphics]::FromImage($small);$scaled.InterpolationMode='HighQualityBicubic';$scaled.DrawImage($bitmap,0,0,$w,$h);$scaled.Dispose();$scaled=$null
 $stream=New-Object System.IO.MemoryStream;$codec=[System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders()|Where-Object MimeType -eq 'image/jpeg'|Select-Object -First 1
 $ep=New-Object System.Drawing.Imaging.EncoderParameters 1;$ep.Param[0]=New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality,[long]$p.quality)
 $small.Save($stream,$codec,$ep);$ep.Dispose()
 if($stream.Length -gt 2097152){throw 'encoded_image_too_large'}
 @{ok=$true;image=[Convert]::ToBase64String($stream.ToArray());metadata=@{captured_at=[DateTime]::UtcNow.ToString('o');origin_x=$bounds.X;origin_y=$bounds.Y;original_width=$bounds.Width;original_height=$bounds.Height;image_width=$w;image_height=$h;scale_x=[double]$w/$bounds.Width;scale_y=[double]$h/$bounds.Height;monitors=$monitors;mime_type='image/jpeg'}}|ConvertTo-Json -Compress -Depth 8|ForEach-Object{[Console]::Write($_)}
}catch{@{ok=$false;error=$_.Exception.Message}|ConvertTo-Json -Compress|ForEach-Object{[Console]::Write($_)}}
finally{if($graphics){$graphics.Dispose()};if($scaled){$scaled.Dispose()};if($bitmap){$bitmap.Dispose()};if($small){$small.Dispose()};if($stream){$stream.Dispose()};if($desktop -ne [IntPtr]::Zero){[CaptureDesktop]::CloseDesktop($desktop)|Out-Null}}

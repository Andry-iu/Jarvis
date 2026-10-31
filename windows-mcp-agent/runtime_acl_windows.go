//go:build windows
package main
import("context";"errors";"os";"os/exec";"os/user";"path/filepath";"time")
func protectRuntimeDirectory(dir string)error{
 u,e:=user.Current();if e!=nil{return errors.New("cannot determine runtime owner")};ctx,cancel:=context.WithTimeout(context.Background(),8*time.Second);defer cancel()
 c:=exec.CommandContext(ctx,filepath.Join(os.Getenv("SystemRoot"),"System32","icacls.exe"),dir,"/inheritance:r","/grant:r","*"+u.Uid+":(OI)(CI)F","*S-1-5-18:(OI)(CI)F");configureDesktopCommand(c)
 if e=c.Run();e!=nil{return errors.New("cannot restrict runtime state ACL to the current account and SYSTEM")};return nil
}

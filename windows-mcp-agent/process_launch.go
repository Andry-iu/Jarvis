package main

import (
 "errors"
 "os"
 "os/exec"
 "path/filepath"
 "strings"
)

func validateProcessRequest(p runtimeRequest) (string, error) {
 if strings.TrimSpace(p.Executable)=="" || strings.ContainsRune(p.Executable,0) || len(p.Args)>128 { return "", errors.New("executable and at most 128 arguments are required") }
 for _,arg:=range p.Args {if strings.ContainsRune(arg,0)||len(arg)>32768{return "",errors.New("invalid process argument")}}
 exe:=p.Executable
 if !filepath.IsAbs(exe) {var err error;exe,err=exec.LookPath(exe);if err!=nil{return "",errors.New("executable not found")}}
 exe,err:=filepath.Abs(exe);if err!=nil{return "",err}
 st,err:=os.Stat(exe);if err!=nil||!st.Mode().IsRegular(){return "",errors.New("executable must be a regular file")}
 if p.WorkingDirectory!="" {if !filepath.IsAbs(p.WorkingDirectory){return "",errors.New("working_directory must be absolute")};st,err=os.Stat(p.WorkingDirectory);if err!=nil||!st.IsDir(){return "",errors.New("working_directory is not a directory")}}
 return exe,nil
}

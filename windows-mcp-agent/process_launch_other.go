//go:build !windows

package main

import "errors"
func launchProcess(p runtimeRequest)(any,error){return nil,errors.New("process_start requires Windows")}
func knownFolder(name string)(any,error){return nil,errors.New("known_folder requires Windows")}

//go:build windows

package main

import (
	"errors"
	"fmt"
	"os/exec"
	"syscall"
	"time"
	"unsafe"
)

var jobKernel = syscall.NewLazyDLL("kernel32.dll")
var createJob = jobKernel.NewProc("CreateJobObjectW")
var setJob = jobKernel.NewProc("SetInformationJobObject")
var assignJob = jobKernel.NewProc("AssignProcessToJobObject")
var terminateJob = jobKernel.NewProc("TerminateJobObject")
var queryJob = jobKernel.NewProc("QueryInformationJobObject")
var threadFirst = jobKernel.NewProc("Thread32First")
var threadNext = jobKernel.NewProc("Thread32Next")
var openThread = jobKernel.NewProc("OpenThread")
var resumeThread = jobKernel.NewProc("ResumeThread")

type threadEntry struct {
	Size, Usage, ID, OwnerPID   uint32
	BasePriority, DeltaPriority int32
	Flags                       uint32
}

func resumeSuspended(pid uint32) error {
	snap, e := syscall.CreateToolhelp32Snapshot(4, 0)
	if e != nil {
		return e
	}
	defer syscall.CloseHandle(snap)
	entry := threadEntry{Size: uint32(unsafe.Sizeof(threadEntry{}))}
	ok, _, _ := threadFirst.Call(uintptr(snap), uintptr(unsafe.Pointer(&entry)))
	for ok != 0 {
		if entry.OwnerPID == pid {
			h, _, err := openThread.Call(2, 0, uintptr(entry.ID))
			if h == 0 {
				return err
			}
			defer syscall.CloseHandle(syscall.Handle(h))
			n, _, err := resumeThread.Call(h)
			if uint32(n) == 0xffffffff {
				return err
			}
			return nil
		}
		ok, _, _ = threadNext.Call(uintptr(snap), uintptr(unsafe.Pointer(&entry)))
	}
	return errors.New("suspended primary thread unavailable")
}

// Start suspended, assign the job, then resume: no compiler/test child can be
// created outside this job. Closing it on a crash kills all descendants.
func runManagedCommand(c *exec.Cmd) error {
	h, _, err := createJob.Call(0, 0)
	if h == 0 {
		return fmt.Errorf("create process job: %w", err)
	}
	defer syscall.CloseHandle(syscall.Handle(h))
	limits := make([]byte, 144)
	*(*uint32)(unsafe.Pointer(&limits[16])) = 0x2000
	ok, _, err := setJob.Call(h, 9, uintptr(unsafe.Pointer(&limits[0])), uintptr(len(limits)))
	if ok == 0 {
		return fmt.Errorf("set process job limits: %w", err)
	}
	if c.SysProcAttr == nil {
		c.SysProcAttr = &syscall.SysProcAttr{}
	}
	c.SysProcAttr.CreationFlags |= 4
	c.Cancel = func() error {
		ok, _, e := terminateJob.Call(h, 1)
		if ok == 0 {
			return e
		}
		return nil
	}
	if e := c.Start(); e != nil {
		return e
	}
	process, e := syscall.OpenProcess(0x101501, false, uint32(c.Process.Pid))
	if e != nil {
		c.Process.Kill()
		c.Wait()
		return e
	}
	ok, _, err = assignJob.Call(h, uintptr(process))
	syscall.CloseHandle(process)
	if ok == 0 {
		c.Process.Kill()
		c.Wait()
		return fmt.Errorf("assign suspended process to job: %w", err)
	}
	if e = resumeSuspended(uint32(c.Process.Pid)); e != nil {
		terminateJob.Call(h, 1)
		c.Wait()
		return e
	}
	runErr := c.Wait()
	terminateJob.Call(h, 1)
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		var accounting [48]byte
		ok, _, e := queryJob.Call(h, 1, uintptr(unsafe.Pointer(&accounting[0])), uintptr(len(accounting)), 0)
		if ok == 0 {
			return fmt.Errorf("cannot verify process-tree termination: %w", e)
		}
		active := *(*uint32)(unsafe.Pointer(&accounting[40]))
		if active == 0 {
			return runErr
		}
		time.Sleep(20 * time.Millisecond)
	}
	return errors.New("process-tree termination not confirmed; outcome uncertain")
}

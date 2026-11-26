//go:build !windows

package main

import "errors"

func ircSerialPorts() ([]ircSerialPort, error) {
	return nil, errors.New("IRCESP32 USB discovery requires a Windows bridge")
}

func ircOpenSerial(ircSerialPort) (ircSerialIO, error) {
	return nil, errors.New("IRCESP32 USB requires a Windows bridge")
}

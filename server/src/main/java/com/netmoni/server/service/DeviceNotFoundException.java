package com.netmoni.server.service;

import java.util.UUID;

public class DeviceNotFoundException extends RuntimeException {
	public DeviceNotFoundException(UUID id) { super("Device not found: " + id); }
}

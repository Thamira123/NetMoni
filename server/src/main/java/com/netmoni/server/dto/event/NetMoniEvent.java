package com.netmoni.server.dto.event;
import java.time.Instant;
public record NetMoniEvent(String event,Instant timestamp,Object data) {}

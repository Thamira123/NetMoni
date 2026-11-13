package com.netmoni.server.dto.agent;
import java.util.List;
public record AuthorizedCidrsRequest(List<String> cidrs) { }

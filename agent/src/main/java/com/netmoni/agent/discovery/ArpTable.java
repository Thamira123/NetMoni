package com.netmoni.agent.discovery;

import com.netmoni.agent.config.Ipv4Cidr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Best-effort read of the OS neighbor cache. It never sends ARP requests itself. */
final class ArpTable {
 private static final Pattern IPV4=Pattern.compile("(?<![0-9])((?:[0-9]{1,3}\\.){3}[0-9]{1,3})(?![0-9])");
 private static final Pattern MAC=Pattern.compile("(?i)(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}");
 private ArpTable(){}
 static Map<String,String> read(Ipv4Cidr subnet){
  String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);List<String> command=os.contains("win")?List.of("arp","-a"):List.of("ip","neigh","show");
  try{Process p=new ProcessBuilder(command).redirectErrorStream(true).start();if(!p.waitFor(1200,TimeUnit.MILLISECONDS)){p.destroyForcibly();return Map.of();}String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> result=new HashMap<>();
   for(String line:output.split("\\R")){Matcher ip=IPV4.matcher(line),mac=MAC.matcher(line);if(ip.find()&&mac.find()){String address=ip.group(1);if(subnet.contains(address))result.put(address,mac.group().replace('-',':').toUpperCase(Locale.ROOT));}}
   return Map.copyOf(result);
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return Map.of();}catch(IOException|RuntimeException ignored){return Map.of();}
 }
}

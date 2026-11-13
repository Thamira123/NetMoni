package com.netmoni.agent.config;

import java.net.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import com.netmoni.agent.model.AgentNetworkInfo;

public final class NetworkInterfaceDiscovery {
    private NetworkInterfaceDiscovery() { }

    public static List<AgentNetworkInfo> discover(List<Ipv4Cidr> authorizedCidrs) {
        List<AgentNetworkInfo> result=new ArrayList<>();
        try {
            Map<String,String> gateways=defaultGateways();
            Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
            if(all==null)return List.of();
            while(all.hasMoreElements()) {
                NetworkInterface ni=all.nextElement();
                boolean up; try { up=ni.isUp(); } catch(SocketException e) { up=false; }
                if(ni.isLoopback())continue;
                String mac=formatMac(ni.getHardwareAddress());
                List<String> ipv6=new ArrayList<>();
                for(InterfaceAddress ia:ni.getInterfaceAddresses()) if(ia.getAddress() instanceof Inet6Address address && !address.isLinkLocalAddress()) ipv6.add(address.getHostAddress().split("%",2)[0]+"/"+ia.getNetworkPrefixLength());
                for(InterfaceAddress ia:ni.getInterfaceAddresses()) {
                    if(!(ia.getAddress() instanceof Inet4Address address))continue;
                    int prefix=ia.getNetworkPrefixLength();
                    Ipv4Cidr actual=new Ipv4Cidr(Ipv4Cidr.parseAddress(address.getHostAddress()),prefix);
                    String cidr=actual.toString();
                    boolean authorized=authorizedCidrs.stream().anyMatch(allowed->containsRange(allowed,actual));
                    String v4=address.getHostAddress();
                    String gateway=gateways.getOrDefault(v4,gateways.get("interface:"+ni.getName()));
                    result.add(new AgentNetworkInfo(ni.getName(),ni.getDisplayName(),typeOf(ni),up?"UP":"DOWN",v4,cidr,mask(prefix),
                            prefix<31?broadcast(actual):null,gateway,mac,ipv6.isEmpty()?null:ipv6.get(0),ipv6.isEmpty()?null:Integer.parseInt(ipv6.get(0).substring(ipv6.get(0).lastIndexOf('/')+1)),authorized,authorized&&up&&prefix>=20,Instant.now()));
                }
            }
        } catch(SocketException | IllegalArgumentException ignored) { return List.copyOf(result); }
        return List.copyOf(result);
    }

    private static boolean containsRange(Ipv4Cidr allowed,Ipv4Cidr actual) { return allowed.contains(Ipv4Cidr.format(actual.network())) && actual.prefixLength()>=allowed.prefixLength(); }
    private static String typeOf(NetworkInterface ni) { String text=(ni.getName()+" "+ni.getDisplayName()).toLowerCase(Locale.ROOT); if(text.contains("wifi")||text.contains("wi-fi")||text.contains("wlan"))return "WIFI"; if(text.contains("ethernet")||text.startsWith("eth")||text.startsWith("en"))return "ETHERNET"; if(ni.isVirtual())return "VIRTUAL"; return "OTHER"; }
    private static String formatMac(byte[] bytes){return bytes==null?null:HexFormat.ofDelimiter(":").withUpperCase().formatHex(bytes);}
    private static String mask(int prefix){long mask=prefix==0?0:(0xffff_ffffL<<(32-prefix))&0xffff_ffffL;return Ipv4Cidr.format(mask);}
    private static String broadcast(Ipv4Cidr cidr){long mask=cidr.prefixLength()==0?0:(0xffff_ffffL<<(32-cidr.prefixLength()))&0xffff_ffffL;return Ipv4Cidr.format(cidr.network()|(~mask&0xffff_ffffL));}
    private static Map<String,String> defaultGateways(){
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);List<String> command=os.contains("win")?List.of("route","print","-4"):List.of("ip","-4","route","show","default");
        try{Process p=new ProcessBuilder(command).redirectErrorStream(true).start();if(!p.waitFor(700,TimeUnit.MILLISECONDS)){p.destroyForcibly();return Map.of();}String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> result=new HashMap<>();
            if(os.contains("win")){for(String line:output.split("\\R")){String[] cols=line.trim().split("\\s+");if(cols.length>=5&&"0.0.0.0".equals(cols[0])&&"0.0.0.0".equals(cols[1])&&cols[3].matches("(?:\\d{1,3}\\.){3}\\d{1,3}"))result.put(cols[3],cols[2]);}}
            else {for(String line:output.split("\\R")){String[] cols=line.trim().split("\\s+");String gateway=null,iface=null;for(int i=0;i<cols.length;i++){if("via".equals(cols[i])&&i+1<cols.length)gateway=cols[i+1];if("dev".equals(cols[i])&&i+1<cols.length)iface=cols[i+1];}if(gateway!=null&&iface!=null)result.put("interface:"+iface,gateway);}}
            return Map.copyOf(result);
        }catch(IOException e){return Map.of();}catch(InterruptedException e){Thread.currentThread().interrupt();return Map.of();}
    }
}

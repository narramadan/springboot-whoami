package com.docker.WhoAmI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;

import java.io.*;
import java.net.*;
import java.util.*;

@SpringBootApplication
@RestController
@RequestMapping("/")
public class WhoAmI {

    public static void main(String[] args) {
        SpringApplication.run(WhoAmI.class, args);
    }

    @RequestMapping(value="/", method= RequestMethod.GET)
    protected String doGet() throws Exception {

        StringBuilder builder = new StringBuilder();
        
        long maxMemory = Runtime.getRuntime().maxMemory();

        builder.append("<br/><br/>Available processors (cores): " + Runtime.getRuntime().availableProcessors())

        .append("<br/><br/>Free memory (bytes): " + Runtime.getRuntime().freeMemory())
        
        .append("<br/>Maximum memory (bytes): " + (maxMemory == Long.MAX_VALUE ? "no limit" : maxMemory))

        .append("<br/>Total memory available to JVM (bytes): " + Runtime.getRuntime().totalMemory());

        File[] roots = File.listRoots();

        for (File root : roots) {
            builder.append("<br/><br/>File system root: " + root.getAbsolutePath())
            .append("<br/>Total space (bytes): " + root.getTotalSpace())
            .append("<br/>Free space (bytes): " + root.getFreeSpace())
            .append("<br/>Usable space (bytes): " + root.getUsableSpace());
        }
        
        builder.append("<br/><br/>Network Details:");
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets)) {
            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                builder.append("<br/>Display name: "+ netint.getDisplayName()).append("\t\tInetAddress: "+ inetAddress);
            }
        }

        // Handling UnknownHostException: name or service not known
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        }
        catch(Exception e) {
            hostName = e.getMessage().split(":")[0];
        }

        return
            "<div style=\"text-align:center;\"><h1>Who Am I - "+hostName+"</h1></div>"
            +"<div style=\"margin-left:10%\">"
                +builder.toString()
            +"</div>";
    }

}
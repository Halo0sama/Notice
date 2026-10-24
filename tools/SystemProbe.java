package com.halo.notice;
public class SystemProbe {
 public static void main(String[] args) throws Exception {
  if(args.length<2) throw new IllegalArgumentException("package uid [channel expected importance]");
  NotificationEngine e=new NotificationEngine();
  if(args.length==5) System.out.println(e.change(args[0],Integer.parseInt(args[1]),args[2],Integer.parseInt(args[3]),Integer.parseInt(args[4])));
  else System.out.println(e.channels(args[0],Integer.parseInt(args[1])));
 }
}

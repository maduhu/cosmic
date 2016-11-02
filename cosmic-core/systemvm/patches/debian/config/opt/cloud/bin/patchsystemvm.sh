#!/usr/bin/env bash

logfile="/var/log/cosmic/agent/patch_systemvm.log"

mkdir -p /var/log/cosmic/agent

# Extract systemvm.zip package to install_directory.
patch_systemvm() {
   local install_directory=/opt/cosmic/agent
   local patchfile=$1
   rm -rf $install_directory
   mkdir -p $install_directory

   echo "All" | unzip $patchfile -d $install_directory >$logfile 2>&1

   find $install_directory -name \*.sh | xargs chmod 555

   [ -f /etc/udev/rules.d/70-persistent-net.rules ] && sudo rm -f /etc/udev/rules.d/70-persistent-net.rules
   [ -f /etc/udev/rules.d/75-persistent-net-generator.rules ] && sudo rm -f /etc/udev/rules.d/75-persistent-net-generator.rules
   return 0
}

consoleproxy_svcs() {
   systemctl enable cloud
   systemctl enable postinit
   systemctl disable cloud-passwd-srvr
   systemctl disable haproxy
   systemctl disable dnsmasq
   systemctl enable ssh
   systemctl disable apache2
   systemctl disable nfs-common
   systemctl disable portmap
   systemctl disable keepalived
   systemctl disable conntrackd
   echo "cloud postinit ssh" > /var/cache/cloud/enabled_svcs
   echo "cloud-passwd-srvr haproxy dnsmasq apache2 nfs-common portmap" > /var/cache/cloud/disabled_svcs
}

secstorage_svcs() {
   systemctl enable cloud
   systemctl enable postinit
   systemctl disable cloud-passwd-srvr
   systemctl disable haproxy
   systemctl disable dnsmasq
   systemctl enable portmap
   systemctl enable nfs-common
   systemctl enable ssh
   systemctl disable apache2
   systemctl disable keepalived
   systemctl disable conntrackd
   echo "cloud postinit ssh nfs-common portmap" > /var/cache/cloud/enabled_svcs
   echo "cloud-passwd-srvr haproxy dnsmasq" > /var/cache/cloud/disabled_svcs
}

routing_svcs() {
   grep "redundant_router=1" /var/cache/cloud/cmdline > /dev/null
   RROUTER=$?
   systemctl disable cloud
   systemctl enable haproxy
   systemctl enable ssh
   systemctl disable nfs-common
   systemctl disable portmap
   echo "ssh haproxy apache2" > /var/cache/cloud/enabled_svcs
   echo "cloud nfs-common portmap" > /var/cache/cloud/disabled_svcs
   if [ $RROUTER -eq 0 ]
   then
       systemctl disable dnsmasq
       systemctl disable cloud-passwd-srvr
       systemctl enable keepalived
       systemctl enable conntrackd
       systemctl enable postinit
       echo "keepalived conntrackd postinit" >> /var/cache/cloud/enabled_svcs
       echo "dnsmasq cloud-passwd-srvr" >> /var/cache/cloud/disabled_svcs
   else
       systemctl enable dnsmasq
       systemctl enable cloud-passwd-srvr
       systemctl disable keepalived
       systemctl disable conntrackd
       echo "dnsmasq cloud-passwd-srvr " >> /var/cache/cloud/enabled_svcs
       echo "keepalived conntrackd " >> /var/cache/cloud/disabled_svcs
   fi
}

dhcpsrvr_svcs() {
   systemctl disable cloud
   systemctl enable cloud-passwd-srvr
   systemctl disable haproxy
   systemctl enable dnsmasq
   systemctl enable ssh
   systemctl disable nfs-common
   systemctl disable portmap
   systemctl disable keepalived
   systemctl disable conntrackd
   echo "ssh dnsmasq cloud-passwd-srvr apache2" > /var/cache/cloud/enabled_svcs
   echo "cloud nfs-common haproxy portmap" > /var/cache/cloud/disabled_svcs
}

enable_pcihotplug() {
   sed -i -e "/shpchp/d" /etc/modules
   #sed -i -e "/hotplug/d" /etc/modules
   echo shpchp >> /etc/modules
   #echo hotplug >> /etc/modules
}

enable_serial_console() {
   sed -i -e "/^serial.*/d" /boot/grub/grub.conf
   sed -i -e "/^terminal.*/d" /boot/grub/grub.conf
   sed -i -e "/^default.*/a\serial --unit=0 --speed=115200 --parity=no --stop=1" /boot/grub/grub.conf
   sed -i -e "/^serial.*/a\terminal --timeout=0 serial console" /boot/grub/grub.conf
   sed -i -e "s/\(^kernel.* ro\) \(console.*\)/\1 console=tty0 console=ttyS0,115200n8/" /boot/grub/grub.conf
   sed -i -e "/^s0:2345:respawn.*/d" /etc/inittab
   sed -i -e "/6:23:respawn/a\s0:2345:respawn:/sbin/getty -L 115200 ttyS0 vt102" /etc/inittab
}


CMDLINE=$(cat /var/cache/cloud/cmdline)
TYPE="router"
PATCH_MOUNT=$1
Hypervisor=$2

for i in $CMDLINE
  do
    # search for foo=bar pattern and cut out foo
    KEY=$(echo $i | cut -d= -f1)
    VALUE=$(echo $i | cut -d= -f2)
    case $KEY in
      type)
        TYPE=$VALUE
        ;;
      *)
        ;;
    esac
done

if ([ "$TYPE" == "consoleproxy" ] || [ "$TYPE" == "secstorage" ])  && [ -f ${PATCH_MOUNT}/systemvm.zip ]
then
  patch_systemvm ${PATCH_MOUNT}/systemvm.zip
  if [ $? -gt 0 ]
  then
    printf "Failed to apply patch systemvm\n" >$logfile
    exit 5
  fi
fi


#empty known hosts
echo "" > /root/.ssh/known_hosts

if [ "$Hypervisor" == "kvm" ]
then
   enable_pcihotplug
   enable_serial_console
fi

if [ "$TYPE" == "router" ] || [ "$TYPE" == "vpcrouter" ]
then
  routing_svcs
  if [ $? -gt 0 ]
  then
    printf "Failed to execute routing_svcs\n" >$logfile
    exit 6
  fi
fi

if [ "$TYPE" == "dhcpsrvr" ]
then
  dhcpsrvr_svcs
  if [ $? -gt 0 ]
  then
    printf "Failed to execute dhcpsrvr_svcs\n" >$logfile
    exit 6
  fi
fi


if [ "$TYPE" == "consoleproxy" ]
then
  consoleproxy_svcs
  if [ $? -gt 0 ]
  then
    printf "Failed to execute consoleproxy_svcs\n" >$logfile
    exit 7
  fi
fi

if [ "$TYPE" == "secstorage" ]
then
  secstorage_svcs
  if [ $? -gt 0 ]
  then
    printf "Failed to execute secstorage_svcs\n" >$logfile
    exit 8
  fi
fi

exit $?

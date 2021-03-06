import socket
import time

from nose.plugins.attrib import attr

from marvin.cloudstackAPI import (
    stopRouter,
    destroyRouter
)
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.lib.base import (
    NetworkACL,
    NATRule,
    PublicIPAddress,
    VirtualMachine,
    Network,
    VPC,
    Account
)
from marvin.lib.common import (
    get_default_network_offering_no_load_balancer,
    get_default_network_offering,
    list_routers,
    list_vlan_ipranges,
    list_networks,
    get_default_vpc_offering,
    get_default_virtual_machine_offering,
    get_template,
    get_zone,
    get_domain
)
from marvin.lib.utils import cleanup_resources
from marvin.utils.MarvinLog import MarvinLog


class TestVPCNics(cloudstackTestCase):
    @classmethod
    def setUpClass(cls):
        cls.logger = MarvinLog(MarvinLog.LOGGER_TEST).get_logger()

        # We want to fail quicker if it's failure
        socket.setdefaulttimeout(60)

        cls.testClient = super(TestVPCNics, cls).getClsTestClient()
        cls.api_client = cls.testClient.getApiClient()

        cls.services = cls.testClient.getParsedTestDataConfig()

        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.api_client)
        cls.zone = get_zone(cls.api_client, cls.testClient.getZoneForTests())
        cls.template = get_template(
            cls.api_client,
            cls.zone.id
        )
        cls.services["virtual_machine"]["zoneid"] = cls.zone.id
        cls.services["virtual_machine"]["template"] = cls.template.id

        cls.service_offering = get_default_virtual_machine_offering(cls.api_client)

        return

    def setUp(self):
        self.routers = []
        self.networks = []
        self.ips = []
        self.apiclient = self.testClient.getApiClient()
        self.account = Account.create(
            self.apiclient,
            self.services["account"],
            admin=True,
            domainid=self.domain.id)

        self.vpc_off = get_default_vpc_offering(self.apiclient)

        self.logger.debug("Creating a VPC network in the account: %s" % self.account.name)
        self.services["vpc"]["cidr"] = '10.1.1.1/16'
        self.vpc = VPC.create(
            self.apiclient,
            self.services["vpc"],
            vpcofferingid=self.vpc_off.id,
            zoneid=self.zone.id,
            account=self.account.name,
            domainid=self.account.domainid)

        self.cleanup = [self.vpc, self.account]
        return

    def tearDown(self):
        try:
            self.destroy_routers()
            cleanup_resources(self.apiclient, self.cleanup)
        except Exception as e:
            self.logger.debug("Warning: Exception during cleanup : %s" % e)
        return

    @attr(tags=['advanced'])
    def test_01_VPC_nics_after_destroy(self):
        """ Create a VPC with two networks with one VM in each network and test nics after destroy"""
        self.logger.debug("Starting test_01_VPC_nics_after_destroy")
        self.query_routers()

        net_off = get_default_network_offering(self.apiclient)
        net1 = self.create_network(net_off, "10.1.1.1")
        net_off_no_lb = get_default_network_offering_no_load_balancer(self.apiclient)
        net2 = self.create_network(net_off_no_lb, "10.1.2.1")

        self.networks.append(net1)
        self.networks.append(net2)

        self.add_nat_rules()
        self.check_ssh_into_vm()

        self.destroy_routers()
        time.sleep(30)

        net1.add_vm(self.deployvm_in_network(net1.get_net()))
        self.query_routers()

        self.add_nat_rules()
        self.check_ssh_into_vm()

    @attr(tags=['advanced'])
    def test_02_VPC_default_routes(self):
        """ Create a VPC with two networks with one VM in each network and test default routes"""
        self.logger.debug("Starting test_02_VPC_default_routes")
        self.query_routers()

        net_off = get_default_network_offering(self.apiclient)
        net1 = self.create_network(net_off, "10.1.1.1")
        net_off_no_lb = get_default_network_offering_no_load_balancer(self.apiclient)
        net2 = self.create_network(net_off_no_lb, "10.1.2.1")

        self.networks.append(net1)
        self.networks.append(net2)

        self.add_nat_rules()
        self.do_default_routes_test()

    def find_public_gateway(self):
        networks = list_networks(self.apiclient,
                                 zoneid=self.zone.id,
                                 listall=True,
                                 issystem=True,
                                 traffictype="Public")
        self.logger.debug('::: Public Networks ::: ==> %s' % networks)

        self.assertTrue(len(networks) == 1, "Test expects only 1 Public network but found -> '%s'" % len(networks))

        ip_ranges = list_vlan_ipranges(self.apiclient,
                                       zoneid=self.zone.id,
                                       networkid=networks[0].id)
        self.logger.debug('::: IP Ranges ::: ==> %s' % ip_ranges)

        self.assertTrue(len(ip_ranges) == 1, "Test expects only 1 VLAN IP Range network but found -> '%s'" % len(ip_ranges))
        self.assertIsNotNone(ip_ranges[0].gateway, "The network with id -> '%s' returned an IP Range with a None gateway. Please check your Datacenter settings." % networks[0].id)

        return ip_ranges[0].gateway

    def query_routers(self):
        self.routers = list_routers(self.apiclient,
                                    account=self.account.name,
                                    domainid=self.account.domainid,
                                    )

        self.assertEqual(
            isinstance(self.routers, list), True,
            "Check for list routers response return valid data")

    def stop_router(self, router):
        self.logger.debug('Stopping router')
        cmd = stopRouter.stopRouterCmd()
        cmd.id = router.id
        cmd.forced = "true"
        self.apiclient.stopRouter(cmd)

    def destroy_routers(self):
        self.logger.debug('Destroying routers')
        for router in self.routers:
            self.stop_router(router)
            cmd = destroyRouter.destroyRouterCmd()
            cmd.id = router.id
            self.apiclient.destroyRouter(cmd)
        self.routers = []

    def create_network(self, network_offering, gateway='10.1.1.1', vpc=None):
        try:
            self.services["network"]["name"] = "NETWORK-" + str(gateway)
            self.logger.debug('Adding Network=%s' % self.services["network"])
            obj_network = Network.create(
                self.apiclient,
                self.services["network"],
                accountid=self.account.name,
                domainid=self.account.domainid,
                networkofferingid=network_offering.id,
                zoneid=self.zone.id,
                gateway=gateway,
                vpcid=vpc.id if vpc else self.vpc.id
            )

            self.logger.debug("Created network with ID: %s" % obj_network.id)
        except Exception, e:
            self.fail('Unable to create a Network with offering=%s because of %s ' % (network_offering.id, e))
        o = networkO(obj_network)

        vm1 = self.deployvm_in_network(obj_network)

        self.cleanup.insert(1, obj_network)

        o.add_vm(vm1)
        return o

    def deployvm_in_network(self, network):
        try:
            self.logger.debug('Creating VM in network=%s' % network.name)
            vm = VirtualMachine.create(
                self.apiclient,
                self.services["virtual_machine"],
                accountid=self.account.name,
                domainid=self.account.domainid,
                serviceofferingid=self.service_offering.id,
                networkids=[str(network.id)]
            )

            self.logger.debug('Created VM=%s in network=%s' % (vm.id, network.name))
            self.cleanup.insert(0, vm)
            return vm
        except:
            self.fail('Unable to create VM in a Network=%s' % network.name)

    def acquire_publicip(self, network):
        self.logger.debug("Associating public IP for network: %s" % network.name)
        public_ip = PublicIPAddress.create(
            self.apiclient,
            accountid=self.account.name,
            zoneid=self.zone.id,
            domainid=self.account.domainid,
            networkid=network.id,
            vpcid=self.vpc.id
        )
        self.logger.debug("Associated %s with network %s" % (
            public_ip.ipaddress.ipaddress,
            network.id
        ))
        return public_ip

    def create_natrule(self, vm, public_ip, network, services=None):
        self.logger.debug("Creating NAT rule in network for vm with public IP")
        if not services:
            services = self.services["natrule_ssh"]
        nat_rule = NATRule.create(
            self.apiclient,
            vm,
            services,
            ipaddressid=public_ip.ipaddress.id,
            openfirewall=False,
            networkid=network.id,
            vpcid=self.vpc.id)

        self.logger.debug("Adding NetworkACL rules to make NAT rule accessible")
        nwacl_nat = NetworkACL.create(
            self.apiclient,
            networkid=network.id,
            services=services,
            traffictype='Ingress'
        )
        self.logger.debug('nwacl_nat=%s' % nwacl_nat.__dict__)
        return nat_rule

    def delete_nat_rules(self):
        for o in self.networks:
            for vm in o.get_vms():
                if vm.get_nat() is not None:
                    vm.get_nat().delete(self.apiclient)
                    vm.set_nat(None)

    def add_nat_rules(self):
        for o in self.networks:
            for vm in o.get_vms():
                if vm.get_ip() is None:
                    vm.set_ip(self.acquire_publicip(o.get_net()))
                if vm.get_nat() is None:
                    vm.set_nat(self.create_natrule(vm.get_vm(), vm.get_ip(), o.get_net()))
                    time.sleep(5)

    def check_ssh_into_vm(self):
        for o in self.networks:
            for vm in o.get_vms():
                try:
                    virtual_machine = vm.get_vm()
                    virtual_machine.ssh_client = None

                    public_ip = vm.get_ip()

                    self.logger.debug("Checking if we can SSH into VM=%s on public_ip=%s" %
                                      (virtual_machine.name, public_ip.ipaddress.ipaddress))

                    virtual_machine.get_ssh_client(ipaddress=public_ip.ipaddress.ipaddress)
                    self.logger.debug("SSH into VM=%s on public_ip=%s is successful" %
                                      (virtual_machine.name, public_ip.ipaddress.ipaddress))
                except:
                    self.fail("Failed to SSH into VM - %s" % (public_ip.ipaddress.ipaddress))

    def do_default_routes_test(self):
        gateway = self.find_public_gateway()
        for o in self.networks:
            for vmObj in o.get_vms():
                ssh_command = "ping -c 3 %s" % gateway

                # Should be able to SSH VM
                result = 'failed'
                try:
                    vm = vmObj.get_vm()
                    public_ip = vmObj.get_ip()
                    self.logger.debug("SSH into VM: %s" % public_ip.ipaddress.ipaddress)

                    ssh = vm.get_ssh_client(ipaddress=public_ip.ipaddress.ipaddress)

                    self.logger.debug("Ping gateway from VM")
                    result = str(ssh.execute(ssh_command))

                    self.logger.debug("SSH result: %s; COUNT is ==> %s" % (result, result.count("3 packets received")))
                except Exception as e:
                    self.fail("SSH Access failed for %s: %s" % (vmObj.get_ip(), e))

                self.assertEqual(result.count("3 packets received"), 1, "Ping gateway from VM should be successful")


class networkO(object):
    def __init__(self, net):
        self.network = net
        self.vms = []

    def get_net(self):
        return self.network

    def add_vm(self, vm):
        self.vms.append(vmsO(vm))

    def get_vms(self):
        return self.vms


class vmsO(object):
    def __init__(self, vm):
        self.vm = vm
        self.ip = None
        self.nat = None

    def get_vm(self):
        return self.vm

    def get_ip(self):
        return self.ip

    def get_nat(self):
        return self.nat

    def set_ip(self, ip):
        self.ip = ip

    def set_nat(self, nat):
        self.nat = nat

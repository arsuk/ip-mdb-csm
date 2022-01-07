The IP MDB based CSM simulation
===============================
The ip-mdb-csm is a simulation of the IP CSM. It is a simple simulation that can handle only simple payments.
It is designed to work with the ip-mdb-client-demo application that simulates a client system. 
The idea is that each of these applications demonstrates the basic principles of Instant Payments. This can be
useful for familiarizing programmers with the basic steps needed to communicate with the CSM. Furthermore, the
ip-mdb-client-demo can be used to interact with the equensWorldline test environment thus verifying a clients
infrastructure and user registratios before attempting to use new software. The ip-mdb-csm can be used to simulate
the CSM if there is no connection to the equensWorldline test environment available for testing yet. 

The ip-mdb-csm consists of message driven beans that simulate the CSM business processes. These are deployed
within an instance of Wildfly. The queue names and related functionality supported are IPOriginatorBean and
IPBeneficiaryBean. These applications deal with a pacs.008 payment request and the resulting pacs.002 response.
The beans listen to queues that are defined in the ip-mdb-csm-x.x.war file ejb-jar.xml file.
The ActiveMQ connections are defined in the Wildfly or JBoss standalone-amq.xml configuration file.

The process flow is as follows:

Client Originator -----------> instantpayments_mybank_originator_payment_request -----------> CSMOriginatorBean

Client Beneficiary <---------- instantpayments_mybank_beneficiary_payment_request <---------- CSMOriginatorBean
           |
           |-----------------> instantpayments_mybank_beneficiary_payment_response ---------> CSMBeneficiaryBean

Client Originator <----------- instantpayments_mybank_originator_payment_response <---------- CSMBeneficiaryBean

Client Beneficiary <---------- instantpayments_mybank_beneficiary_payment_confirmation <----- CSMBeneficiaryBean

Note that the queue names used are configurable as described below in the 'Running the demo' section.

The request must use debtor (originator) and creditor BICs that are known to the ip-mdb-csm application.

The package also includes an IPechoRresponseBean. It creates a CSM's echo request for each echo request queue that
is configured. The client echo code response queue can respond and they will be marked online in the DB. This
is for testing purposes only and has no further impact.

CSMTimedEchoRequest ----> instantpayments_mybank_echo_request ----------------- Client software
                                                                                  |
CSMechoResponseBean <---- instantpayments_mybank_echo_response <------------------|


pacs message types
==================
The CSM demo expects pacs.008 message as input and replies with pacs.002 messages. Some limited validations are perfomed
but incorrect xml will usually result in stack traces.

CSMDBsessionBean
================
The CSM beans call the CSMDBsessionBean to log all the messages and to check is incoming messages are unique. It will do
this only if a database resource is defined. This is set with the JBoss/Wildfly naming variable global/IPdatasorce in the
example standalone-amq.xml file.
There is a an example datasource configured using the H2 database server supplied with JBoss/Wildfly.

CSMOriginatorBean
=================
This bean reads the originator pacs.008 message forwards it to the client beneficiary process after validations. If the message
has already expired (>7s) or if it fails validation a reject is sent to the client bank originator response queue.
If the message is forwarded successfully a second timer message is send to the CSMTimeoutBean that will arrive after 7s. This 
will be cancelled by the CSMTimoutBean if the beneficiary has responded on time.

CSMBeneficiaryBean
==================
This bean receives the response from the client beneficiary response queue. If the timeout task has already sent a response then
the bean does nothing. If the message is too old (>7s) or contains a reject format message then a reject is sent to the client
originator response queue, otherwise and accept message is send and a confirmation message is returned to the beneficiary.

CSMTimeoutBean
==============
The timeout bean receives a duplicate of the payment request that is sent to the beneficiary. The message is delayed so it is
delivered after 7s. If it can insert this in the DB then there has been no other response so the bean can send a timeout
reject to the originator. Otherwise, the bean does nothing and the timeout message is ignored. 

Running the demo
----------------
First the standalone-amq.xml file has been adjusted with the required ActiveMQ broker address, user name and password, and the
ejb-jar.xml has been adjusted to match the queue names you want. The ip-mdb-csm-x.x.war file that contains the ejb-jar.xml
in the the Wildfly subdirectory standalone/deployments. You can replace the XML using an archive manager.
The standalone-amq.xml file is in the subdirectory standalone/configuration.
To run the Wildfly server you simply have to run the 'start' command file in the wildfly directory.

System Property Settings
------------------------
The following system properties are available for test purposes like problem / timeout simulation:
    <property name="IPCSMdatasource" value="jboss/datasources/ExampleDS"/>	# Data source, for example the H2 default Wildfly DB service
    <property name="CSMliquiditySQL" value="true"/> # Set false to use memory cache instead of SQL (H2)
    <property name="ActiveMQhostStr" value="tcp://localhost:61618"/> # If set, avoid using the Wildfly connection pool for message send
    # Note that there are also ActiveMQuser and ActiveMQpassword system variables but these are not usually necessary  
    <property name="ConnectionFactory" value="/jms/ConnectionFactory"/>	# ActimeMQ connection pool for message sends
    # The above is the name of the example connection pool provided by Wildfly.
System properties can be set in a standalone.xml file or as parameters on the command line invoking Wildfly. Wildfly standalone examples
are provided for ActiveMQ (amq), ActiveMQ Artemis (artemis), InVm queues (myfull), with and without an external H2 server.


The IP MDB based CSM simulation
===============================
The ip-mdb-csm is a simulation of the IP CSM. It is a simple simultaion that can handle only simple payments.
It is designed to work with the ip-mdb-demo application that simulates a client system. 
The idea is that each of these applications demonstrates the basic principles of Instant Payments. This can be
useful for familiarizing programmers with the basic steps needed to communicate with the CSM. Furthermore, the
ip-mdb-demo can be used to interact with the equensWorldline test environment thus verifying a clients infrastructure
and user registratios before attempting to use new software. The ip-mdb-csm can be used to simulate the CSM if there 
is not connection to the equensWorldline test evnironment yet.

The ip-mdb-csem consisists of message driven beans that simulate the client business processes. These are deployed
within an instance of Wildfly. The queue names and related functionality supported are IPOriginatorBean and
IPBeneficiaryBean. These applications deal with a pacs.008 payment request and the resulting pacs.002 response.
The beans listen queues that are defined in the ip-mdb-csm-x.x.war file ejb-jar.xml file. The ActiveMQ connections are defined in the Wildfly or JBoss standalone-amq.xml configuration file.

The process flow is as follows:

Client Originator ---------------> ip_mybank_originator_payment_request -----------> IP CSM

Client Beneficiary <-------------- ip_mybank_beneficiary_payment_request <---------- IP CSM
           |
           |---------------------> ip_mybank_beneficiary_payment_response ---------> IP CSM

Client Originator <--------------- ip_mybank_originator_payment_response <---------- IP CSM

Client Beneficiary <-------------- ip_mybank_beneficiary_payment_confirmation <----- IP CSM

Note that the queue names used are configurable as described below in the 'Running the demo' section.

The request must use debtor (originator) and creditor BICs that are known to the ip-mdb-csm application. These queues
wll be configured in the standalone.xml as system properties.

The package also includes an IPechoRresponseBean. It creates a CSM's echo request for each echo request queue that
is configured. The client echo code response queue can respond and they will be marked online in the DB. This
is for testing purposes only and has no further impact.

CSM ---------------> ip_mybank_echo_request ----------------- Client software
                                                                     |
CSM <--------------- ip_mybank_echo_response <-----------------------|


pacs template files
===================
The pacs.008.xml template should be modified to use the BIC of the test bank and the BIC of the creditor bank. The message
ID and transaction ID will be replaced by the test servlet while creating test messages. The servlet alows you to define a template parameter so you can have differing versions for a number of beneficiary banks or to test other IP options.
The pacs.002.xml template must be configured with the test bank's BIC.

After Instant Payments has forwarded the pacs.008 to the test
bank beneficiary request queue the IPbeneficiaryRequestBean will read it. It will then create the pacs.002.xml response
using the template and it will copy the originator fields to the pacs.008 which wll be send to the beneficiary response
queue. The pacs.008 must use the debtor BIC that Instant Payments expects and the pacs.002 must use the creditor BIC of
the creditor bank. See the template files and their XML comments.

IPDBsessionBean
=============
The IPoriginatorRequestBean calls the DBsessionBean to check is incoming messages are unique. It will do this only if
a database resource is defined. This is set with the JBoss/Wildfly naming variable global/IPdatasorce in the example
standalone-amq.xml file.
There is a an example datasource configured using the H2 database server supplied with JBoss/Wildfly.


IPBeneficiaryBean
========================
This bean reads the originator pacs.008 message and responds with a pacs.002 message. The status will be accept if the value
is less than or equal to 100 and reject if it is greater than 100.

IPOriginatorBean
========================
This bean simply reads the response messages and increments a count of messages. It also logs the count for the last 10
seconds in the
Wildfly server.log if there are messages to count.
The messages can be logged if the environment variable global/IPoriginatorLog is set with a file name in the standalone-amq.xml file.

Running the demo
----------------
First the standalone-amq.xml file has been adjusted with the required ActiveMQ broker address, user name and password, and the
ejb-jar.xml has been adjusted to match the queue names you want. The ip-mdb-csm-x.x.war file that contains the ejb-jar.xml
is the the Wildfly subdirectory standalone/deployments. You need to replace the XML using an archive manager. The standalone-amq.xml
file is in the subdirectory standalone/configuration.
To run the Wildfly server you simply have to run the 'start' command file in the wildfly directory.




/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.camel.audit.triplestore.integration;

import static java.lang.Integer.parseInt;
import static java.util.Arrays.asList;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.vocabulary.RDF.type;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_AGENT;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_DATE_TIME;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_EVENT_TYPE;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_EVENT_ID;
import static org.fcrepo.camel.FcrepoHeaders.FCREPO_URI;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

import org.apache.camel.Produce;
import org.apache.camel.Exchange;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.jena.fuseki.embedded.FusekiEmbeddedServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.sparql.core.DatasetImpl;
import org.fcrepo.camel.audit.triplestore.AuditHeaders;
import org.fcrepo.camel.audit.triplestore.AuditSparqlProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Represents an integration test for interacting with an external triplestore.
 *
 * @author Aaron Coburn
 * @since Nov 8, 2014
 */
public class AuditSparqlIT extends CamelTestSupport {

    final private Logger logger = getLogger(AuditSparqlIT.class);

    private static final int FUSEKI_PORT = parseInt(System.getProperty(
            "fuseki.dynamic.test.port", "8080"));

    private static FusekiEmbeddedServer server = null;

    private static final String PREMIS = "http://www.loc.gov/premis/rdf/v1#";

    private static final String USER = "bypassAdmin";

    private static final String USER_AGENT = "curl/7.37.1";

    private static final String EVENT_BASE_URI = "http://example.com/event";

    private static final String EVENT_ID = "ab/cd/ef/gh/abcdefgh12345678";

    private static final String EVENT_URI = EVENT_BASE_URI + "/" + EVENT_ID;

    private static final String AS_NS = "https://www.w3.org/ns/activitystreams#";

    @EndpointInject(uri = "mock:sparql.update")
    protected MockEndpoint sparqlUpdateEndpoint;

    @EndpointInject(uri = "mock:sparql.query")
    protected MockEndpoint sparqlQueryEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Before
    public void setup() throws Exception {
        final Dataset ds = new DatasetImpl(createDefaultModel());
        server = FusekiEmbeddedServer.create().setPort(FUSEKI_PORT).setContextPath("/fuseki").add("/test", ds).build();
        logger.info("Starting EmbeddedFusekiServer on port {}", FUSEKI_PORT);
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        logger.info("Stopping EmbeddedFusekiServer");
        server.stop();
    }

    private Map<String, Object> getEventHeaders() {
        // send an audit event to an external triplestore
        final Map<String, Object> headers = new HashMap<>();
        headers.put(FCREPO_URI, "http://localhost/rest/foo");
        headers.put(FCREPO_EVENT_TYPE, asList(AS_NS + "Create", AS_NS + "Update"));
        headers.put(FCREPO_DATE_TIME, "2015-04-10T14:30:36Z");
        headers.put(FCREPO_AGENT, asList(USER, USER_AGENT));
        headers.put(FCREPO_EVENT_ID, EVENT_ID);

        return headers;
    }

    @Test
    public void testAuditEventTypeTriples() throws Exception {
        sparqlUpdateEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        sparqlQueryEndpoint.expectedMessageCount(1);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
        sparqlQueryEndpoint.expectedBodiesReceivedInAnyOrder(
                "http://id.loc.gov/vocabulary/preservation/eventType/cre");

        template.sendBody("direct:clear", null);
        template.sendBodyAndHeaders(null, getEventHeaders());

        template.sendBodyAndHeader("direct:query", null, Exchange.HTTP_QUERY,
                "query=SELECT ?o WHERE { <" + EVENT_URI + "> <" + PREMIS + "hasEventType> ?o }");

        sparqlQueryEndpoint.assertIsSatisfied();
        sparqlUpdateEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAuditEventRelatedTriples() throws Exception {
        sparqlUpdateEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        sparqlQueryEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
        sparqlQueryEndpoint.expectedBodiesReceivedInAnyOrder(
                "http://localhost/rest/foo"
            );

        template.sendBody("direct:clear", null);
        template.sendBodyAndHeaders(null, getEventHeaders());

        template.sendBodyAndHeader("direct:query", null, Exchange.HTTP_QUERY,
                "query=SELECT ?o WHERE { <" + EVENT_URI + "> <" + PREMIS + "hasEventRelatedObject> ?o }");

        sparqlQueryEndpoint.assertIsSatisfied();
        sparqlUpdateEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAuditEventDateTriples() throws Exception {
        sparqlUpdateEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        sparqlQueryEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
        sparqlQueryEndpoint.expectedBodiesReceivedInAnyOrder(
                "2015-04-10T14:30:36Z"
            );

        template.sendBody("direct:clear", null);
        template.sendBodyAndHeaders(null, getEventHeaders());

        template.sendBodyAndHeader("direct:query", null, Exchange.HTTP_QUERY,
                "query=SELECT ?o WHERE { <" + EVENT_URI + "> <" + PREMIS + "hasEventDateTime> ?o }");

        sparqlQueryEndpoint.assertIsSatisfied();
        sparqlUpdateEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAuditEventAgentTriples() throws Exception {
        sparqlUpdateEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        sparqlQueryEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
        sparqlQueryEndpoint.expectedBodiesReceivedInAnyOrder(
                USER,
                USER_AGENT
            );

        template.sendBody("direct:clear", null);
        template.sendBodyAndHeaders(null, getEventHeaders());

        template.sendBodyAndHeader("direct:query", null, Exchange.HTTP_QUERY,
                "query=SELECT ?o WHERE { <" + EVENT_URI + "> <" + PREMIS + "hasEventRelatedAgent> ?o }");

        sparqlQueryEndpoint.assertIsSatisfied();
        sparqlUpdateEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAuditEventAllTriples() throws Exception {
        sparqlUpdateEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        sparqlQueryEndpoint.expectedMessageCount(8);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        template.sendBody("direct:clear", null);
        template.sendBodyAndHeaders(null, getEventHeaders());

        template.sendBodyAndHeader("direct:query", null, Exchange.HTTP_QUERY,
                "query=SELECT ?o WHERE { <" + EVENT_URI + "> ?p ?o }");

        sparqlQueryEndpoint.assertIsSatisfied();
        sparqlUpdateEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAuditTypeTriples() throws Exception {
        sparqlUpdateEndpoint.expectedMessageCount(2);
        sparqlUpdateEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        sparqlQueryEndpoint.expectedMessageCount(2);
        sparqlQueryEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
        sparqlQueryEndpoint.expectedBodiesReceivedInAnyOrder(
                "http://www.loc.gov/premis/rdf/v1#Event",
                "http://fedora.info/definitions/v4/audit#InternalEvent",
                "http://www.w3.org/ns/prov#InstantaneousEvent"
            );

        template.sendBody("direct:clear", null);
        template.sendBodyAndHeaders(null, getEventHeaders());

        template.sendBodyAndHeader("direct:query", null, Exchange.HTTP_QUERY,
                "query=SELECT ?o WHERE { <" + EVENT_URI + "> <" + type.toString() + "> ?o }");

        sparqlQueryEndpoint.assertIsSatisfied();
        sparqlUpdateEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        final Namespaces ns = new Namespaces("sparql", "http://www.w3.org/2005/sparql-results#");

        final XPathBuilder xpath = new XPathBuilder(
                "//sparql:result/sparql:binding[@name='o']");
        xpath.namespaces(ns);

        final XPathBuilder uriResult = new XPathBuilder(
                "/sparql:binding/sparql:uri");
        uriResult.namespaces(ns);

        final XPathBuilder literalResult = new XPathBuilder(
                "/sparql:binding/sparql:literal");
        literalResult.namespaces(ns);

        return new RouteBuilder() {
            public void configure() throws IOException {
                final String fuseki_url = "http4://localhost:" + Integer.toString(FUSEKI_PORT);

                from("direct:start")
                    .setHeader(AuditHeaders.EVENT_BASE_URI, constant(EVENT_BASE_URI))
                    .process(new AuditSparqlProcessor())
                    .to(fuseki_url + "/fuseki/test/update")
                    .to("mock:sparql.update");

                from("direct:query")
                    .to(fuseki_url + "/fuseki/test/query")
                    .split(xpath)
                        .choice()
                            .when().xpath("/sparql:binding/sparql:uri", String.class, ns)
                                .transform().xpath("/sparql:binding/sparql:uri/text()", String.class, ns)
                                .to("mock:sparql.query")
                            .when().xpath("/sparql:binding/sparql:literal", String.class, ns)
                                .transform().xpath("/sparql:binding/sparql:literal/text()", String.class, ns)
                                .to("mock:sparql.query");

                from("direct:clear")
                    .transform().constant("update=DELETE WHERE { ?s ?o ?p }")
                    .setHeader(Exchange.CONTENT_TYPE).constant("application/x-www-form-urlencoded")
                    .setHeader(Exchange.HTTP_METHOD).constant("POST")
                    .to(fuseki_url + "/fuseki/test/update")
                    .to("mock:sparql.update");

            }
        };
    }
}

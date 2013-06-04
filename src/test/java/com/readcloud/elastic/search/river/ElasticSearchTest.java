package com.readcloud.elastic.search.river;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readcloud.elastic.search.river.dto.Book;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.joda.time.DateTime;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ElasticSearchTest {

    private static final Logger LOG = Logger.getLogger(ElasticSearchTest.class);

    private static final boolean USE_EMBEDDED_NODE = false;
    private static final String HOST = "localhost";
    private static final int PORT = 9300;

    private static Client client;
    private static Node node;

    @BeforeClass
    public static void beforeClass() {
        if (USE_EMBEDDED_NODE) {
            node = NodeBuilder.nodeBuilder().clusterName("test-cluster").local(true).node();
            client = node.client();
        } else {
            client = new TransportClient()
                    .addTransportAddress(new InetSocketTransportAddress(HOST, PORT));
        }
    }

    @AfterClass
    public static void afterClass() {
        if (USE_EMBEDDED_NODE) {
            node.close();
        } else {
            client.close();
        }
    }

    @Test
    public void shouldIndexAndSearch() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        Book book = new Book();
        book.setName("Hitch hiker's guide to the galaxy");
        book.setAuthor("Douglas Adams");
        book.setPublicationDate(new DateTime().withDate(1980, 1, 1).toDate());

        IndexResponse indexResponse = client.prepareIndex("bookindex", "book", "1")
                .setSource(mapper.writeValueAsString(book))
                .execute()
                .actionGet();

        assertEquals("1", indexResponse.getId());

        book.setName("restaurant at the end of the universe");
        indexResponse = client.prepareIndex("bookindex", "book", "2")
                .setSource(mapper.writeValueAsString(book))
                .execute()
                .actionGet();

        assertEquals("2", indexResponse.getId());


        //search for books

        QueryStringQueryBuilder queryStringQueryBuilder = QueryBuilders.queryString("Douglas").defaultOperator(QueryStringQueryBuilder.Operator.AND).
                field("author").
                allowLeadingWildcard(false).useDisMax(true);

        SearchResponse response = client.prepareSearch("bookindex")
                .setTypes("book")
                .setQuery(queryStringQueryBuilder.toString())
                .execute()
                .actionGet();

        assertEquals(2, response.getHits().getTotalHits());

        LOG.info("Search hits : " + response.getHits().getTotalHits());
        LOG.info("Search took : " + response.getTook().format());

        for (SearchHit searchHit : response.getHits()) {
            Book foundBook = mapper.readValue(searchHit.getSourceAsString().getBytes(), Book.class);
            LOG.info("Found book : " + foundBook.toString());
        }

        LOG.info("Books deleted.");
    }

}

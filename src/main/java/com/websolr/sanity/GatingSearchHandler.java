package com.websolr.sanity;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

public final class GatingSearchHandler extends SearchHandler {

	private int initialSuccesses = 0;
	public static final int MINIMUM_SUCCESSES = 50;
	private final ReentrantLock lock = new ReentrantLock();

	@Override
	public final void handleRequestBody(final SolrQueryRequest req,
			final SolrQueryResponse rsp) throws Exception {
		if (initialSuccesses > MINIMUM_SUCCESSES) {
			super.handleRequestBody(req, rsp);
		} else {
			final SolrParams params = req.getParams();
			final boolean distrib = params.getBool("distrib", false)
					|| params.get(ShardParams.SHARDS) != null;
			if (distrib) {
				super.handleRequestBody(req, rsp);
			} else if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
				log.info("Warming " + initialSuccesses + " of "
						+ MINIMUM_SUCCESSES);
				try {
					super.handleRequestBody(req, rsp);
					if (rsp.getException() == null) {
						initialSuccesses++;
					} else {
						initialSuccesses = 0;
					}
				} finally {
					lock.unlock();
				}
			} else {
				rsp.setException(new HandlerException(
						"SearchHandler not yet warm"));
			}
		}
	}
}

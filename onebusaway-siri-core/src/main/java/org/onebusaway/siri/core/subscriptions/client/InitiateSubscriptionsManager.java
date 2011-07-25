package org.onebusaway.siri.core.subscriptions.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.SiriClientRequest;
import org.onebusaway.siri.core.SiriLibrary;
import org.onebusaway.siri.core.exceptions.SiriSubscriptionModuleTypeConflictException;
import org.onebusaway.siri.core.subscriptions.SubscriptionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;

class InitiateSubscriptionsManager extends AbstractManager {

  private static final Logger _log = LoggerFactory.getLogger(InitiateSubscriptionsManager.class);

  /**
   * This map contains pending subscription requests, awaiting a response from
   * the server
   */
  private ConcurrentMap<SubscriptionId, ClientPendingSubscription> _pendingSubscriptionRequests = new ConcurrentHashMap<SubscriptionId, ClientPendingSubscription>();

  public void registerPendingSubscription(SiriClientRequest request,
      SubscriptionRequest subscriptionRequest) {

    _log.debug("register pending subscription request");

    Map<SubscriptionId, ClientPendingSubscription> pendingSubscriptions = new HashMap<SubscriptionId, ClientPendingSubscription>();

    for (ESiriModuleType moduleType : ESiriModuleType.values()) {

      List<AbstractSubscriptionStructure> requests = SiriLibrary.getSubscriptionRequestsForModule(
          subscriptionRequest, moduleType);

      for (AbstractSubscriptionStructure subRequest : requests) {

        SubscriptionId subId = _support.getSubscriptionIdForSubscriptionRequest(
            subscriptionRequest, subRequest);

        /**
         * Check to make sure that the new subscription doesn't conflict with an
         * existing subscription, either active or pending
         */
        checkForModuleTypeConflict(subId, moduleType, pendingSubscriptions);

        ClientPendingSubscription pending = new ClientPendingSubscription(
            subId, request, moduleType, subRequest);
        pendingSubscriptions.put(subId, pending);
      }
    }

    /**
     * We hold off on adding the pending subscriptions until we are sure that
     * none of them cause conflicts.
     */
    _pendingSubscriptionRequests.putAll(pendingSubscriptions);

    /**
     * Setup an expiration timeout for the subscription response
     */
    PendingSubscriptionTimeoutTask task = new PendingSubscriptionTimeoutTask(
        pendingSubscriptions.keySet());
    _subscriptionManager.scheduleResponseTimeoutTask(task);
  }

  public void handleSubscriptionResponse(SubscriptionResponseStructure response) {

    for (StatusResponseStructure status : response.getResponseStatus()) {

      SubscriptionId subId = _support.getSubscriptionIdForStatusResponse(status);

      ClientPendingSubscription pending = _pendingSubscriptionRequests.remove(subId);

      if (pending == null) {
        _support.logUnknownSubscriptionResponse(response, subId);
        continue;
      }

      if (status.isStatus()) {

        _subscriptionManager.upgradePendingSubscription(response, status,
            subId, pending);

      } else {
        _support.logErrorInSubscriptionResponse(response, status, subId);
      }
    }
  }

  /****
   * Private Methods
   ****/

  /**
   * Check to make sure that a new subscription request doesn't conflict with an
   * existing subscription, either active or pending.
   * 
   * @throws SiriSubscriptionModuleTypeConflictException if an exception is
   *           found
   */
  private void checkForModuleTypeConflict(SubscriptionId subId,
      ESiriModuleType moduleType,
      Map<SubscriptionId, ClientPendingSubscription> pendingSubscriptions)
      throws SiriSubscriptionModuleTypeConflictException {

    ESiriModuleType existingModuleType = _subscriptionManager.getModuleTypeForSubscriptionId(subId);

    if (existingModuleType != null && existingModuleType != moduleType) {
      _support.logWarningAboutActiveSubscriptionsWithDifferentModuleTypes(
          subId, moduleType, existingModuleType);
      throw new SiriSubscriptionModuleTypeConflictException(subId,
          existingModuleType, moduleType);
    }

    ClientPendingSubscription pending = _pendingSubscriptionRequests.get(subId);
    if (pending != null && pending.getModuleType() != moduleType) {
      _support.logWarningAboutPendingSubscriptionsWithDifferentModuleTypes(
          subId, moduleType, pending);
      throw new SiriSubscriptionModuleTypeConflictException(subId,
          pending.getModuleType(), moduleType);
    }

    pending = pendingSubscriptions.get(subId);
    if (pending != null && pending.getModuleType() != moduleType) {
      _support.logWarningAboutPendingSubscriptionsWithDifferentModuleTypes(
          subId, moduleType, pending);
      throw new SiriSubscriptionModuleTypeConflictException(subId,
          pending.getModuleType(), moduleType);
    }
  }

  /****
   * 
   ****/

  private class PendingSubscriptionTimeoutTask implements Runnable {

    private final List<SubscriptionId> _subscriptionIds;

    public PendingSubscriptionTimeoutTask(Set<SubscriptionId> subscriptionIds) {
      _subscriptionIds = new ArrayList<SubscriptionId>(subscriptionIds);
    }

    @Override
    public void run() {
      for (SubscriptionId subscriptionId : _subscriptionIds) {
        ClientPendingSubscription pending = _pendingSubscriptionRequests.remove(subscriptionId);
        if (pending != null) {
          _log.warn("pending subscription expired before receiving a subscription response from server: "
              + subscriptionId);
        }
      }
    }
  }
}
#!/usr/bin/env node
/**
 * Workflow Orchestrator & Kafka Standalone Mock Server
 * 
 * Provides high-fidelity mock implementations of all Workflow Orchestrator
 * and Kafka Management REST APIs. Guarantees complete field consistency:
 * - workflowId & id
 * - workflow & workflowName
 * - state & status
 * - stepName & name
 * - complete stepConfigs with retry policies and circuit breaker configurations
 */

import http from 'node:http';
import { parse as parseUrl } from 'node:url';

const PORT = parseInt(process.env.MOCK_PORT || process.env.PORT || '8080', 10);
const HOST = process.env.MOCK_HOST || '0.0.0.0';

// In-memory mock database
const mockDefinitions = {
  'order-fulfillment': {
    workflow: 'order-fulfillment',
    workflowName: 'order-fulfillment',
    version: '1.2.0',
    description: 'High-throughput e-commerce order processing pipeline',
    routes: [
      { triggerStep: 'validate-order', mode: 'sequential', branches: ['reserve-inventory'] },
      { triggerStep: 'reserve-inventory', mode: 'sequential', branches: ['process-payment'] },
      { triggerStep: 'process-payment', mode: 'PARALLEL', branches: ['dispatch-shipping', 'send-notifications'] }
    ],
    steps: [
      { name: 'validate-order', stepName: 'validate-order', type: 'TASK', dependencies: [] },
      { name: 'reserve-inventory', stepName: 'reserve-inventory', type: 'TASK', dependencies: ['validate-order'] },
      { name: 'process-payment', stepName: 'process-payment', type: 'TASK', dependencies: ['reserve-inventory'] },
      { name: 'dispatch-shipping', stepName: 'dispatch-shipping', type: 'TASK', dependencies: ['process-payment'] },
      { name: 'send-notifications', stepName: 'send-notifications', type: 'TASK', dependencies: ['process-payment'] }
    ],
    stepConfigs: {
      'validate-order': {
        async: false,
        retryEnabled: true,
        maxAttempts: 3,
        delayMillis: 1000,
        overridden: false,
        circuitBreakerEnabled: true,
        circuitBreakerName: 'order-validation-cb',
        circuitBreakerState: 'CLOSED',
        circuitBreakerWaitOpenMillis: 5000,
        circuitBreakerPermittedHalfOpenCalls: 2
      },
      'reserve-inventory': {
        async: false,
        retryEnabled: true,
        maxAttempts: 3,
        delayMillis: 1500,
        overridden: true,
        circuitBreakerEnabled: true,
        circuitBreakerName: 'inventory-service-cb',
        circuitBreakerState: 'CLOSED',
        circuitBreakerWaitOpenMillis: 8000,
        circuitBreakerPermittedHalfOpenCalls: 2
      },
      'process-payment': {
        async: true,
        retryEnabled: true,
        maxAttempts: 3,
        delayMillis: 2000,
        overridden: true,
        circuitBreakerEnabled: true,
        circuitBreakerName: 'payment-gateway-cb',
        circuitBreakerFallback: 'fallback-queue-payment',
        circuitBreakerState: 'HALF_OPEN',
        circuitBreakerWaitOpenMillis: 10000,
        circuitBreakerPermittedHalfOpenCalls: 3
      },
      'dispatch-shipping': {
        async: true,
        retryEnabled: false,
        maxAttempts: 1,
        delayMillis: 0,
        overridden: false,
        circuitBreakerEnabled: false
      },
      'send-notifications': {
        async: true,
        retryEnabled: true,
        maxAttempts: 5,
        delayMillis: 3000,
        overridden: true,
        circuitBreakerEnabled: false
      }
    }
  },
  'customer-onboarding': {
    workflow: 'customer-onboarding',
    workflowName: 'customer-onboarding',
    version: '2.0.1',
    description: 'Identity verification, KYC screening, and account setup',
    routes: [
      { triggerStep: 'ingest-profile', mode: 'PARALLEL', branches: ['verify-identity', 'aml-sanctions-check'], joinStep: 'provision-tenant' },
      { triggerStep: 'provision-tenant', mode: 'sequential', branches: ['send-welcome-kit'] }
    ],
    steps: [
      { name: 'ingest-profile', stepName: 'ingest-profile', type: 'TASK', dependencies: [] },
      { name: 'verify-identity', stepName: 'verify-identity', type: 'TASK', dependencies: ['ingest-profile'] },
      { name: 'aml-sanctions-check', stepName: 'aml-sanctions-check', type: 'TASK', dependencies: ['ingest-profile'] },
      { name: 'provision-tenant', stepName: 'provision-tenant', type: 'TASK', dependencies: ['verify-identity', 'aml-sanctions-check'] },
      { name: 'send-welcome-kit', stepName: 'send-welcome-kit', type: 'TASK', dependencies: ['provision-tenant'] }
    ],
    stepConfigs: {
      'ingest-profile': { async: false, retryEnabled: true, maxAttempts: 2, delayMillis: 500, circuitBreakerEnabled: false },
      'verify-identity': { async: true, retryEnabled: true, maxAttempts: 3, delayMillis: 2000, circuitBreakerEnabled: true, circuitBreakerName: 'id-verify-cb', circuitBreakerState: 'CLOSED' },
      'aml-sanctions-check': { async: true, retryEnabled: true, maxAttempts: 4, delayMillis: 1500, overridden: true, circuitBreakerEnabled: true, circuitBreakerName: 'sanctions-cb', circuitBreakerState: 'HALF_OPEN' },
      'provision-tenant': { async: false, retryEnabled: true, maxAttempts: 3, delayMillis: 1000, circuitBreakerEnabled: false },
      'send-welcome-kit': { async: true, retryEnabled: true, maxAttempts: 3, delayMillis: 5000, circuitBreakerEnabled: false }
    }
  },
  'payment-reconciliation': {
    workflow: 'payment-reconciliation',
    workflowName: 'payment-reconciliation',
    version: '1.0.4',
    description: 'Automated settlement and ledger reconciliation with banking gateway',
    routes: [
      { triggerStep: 'fetch-gateway-batch', mode: 'sequential', branches: ['match-transactions'] },
      { triggerStep: 'match-transactions', mode: 'sequential', branches: ['resolve-discrepancies'] },
      { triggerStep: 'resolve-discrepancies', mode: 'sequential', branches: ['post-ledger-entries'] },
      { triggerStep: 'post-ledger-entries', mode: 'sequential', branches: ['archive-batch'] }
    ],
    steps: [
      { name: 'fetch-gateway-batch', stepName: 'fetch-gateway-batch', type: 'TASK', dependencies: [] },
      { name: 'match-transactions', stepName: 'match-transactions', type: 'TASK', dependencies: ['fetch-gateway-batch'] },
      { name: 'resolve-discrepancies', stepName: 'resolve-discrepancies', type: 'TASK', dependencies: ['match-transactions'] },
      { name: 'post-ledger-entries', stepName: 'post-ledger-entries', type: 'TASK', dependencies: ['resolve-discrepancies'] },
      { name: 'archive-batch', stepName: 'archive-batch', type: 'TASK', dependencies: ['post-ledger-entries'] }
    ],
    stepConfigs: {
      'fetch-gateway-batch': { async: true, retryEnabled: true, maxAttempts: 5, delayMillis: 5000, circuitBreakerEnabled: true, circuitBreakerName: 'banking-gateway-cb', circuitBreakerState: 'CLOSED' },
      'match-transactions': { async: false, retryEnabled: true, maxAttempts: 2, delayMillis: 1000, circuitBreakerEnabled: false },
      'resolve-discrepancies': { async: false, retryEnabled: true, maxAttempts: 3, delayMillis: 2000, circuitBreakerEnabled: false },
      'post-ledger-entries': { async: true, retryEnabled: true, maxAttempts: 3, delayMillis: 3000, circuitBreakerEnabled: true, circuitBreakerName: 'ledger-cb', circuitBreakerState: 'CLOSED' },
      'archive-batch': { async: true, retryEnabled: false, maxAttempts: 1, delayMillis: 0, circuitBreakerEnabled: false }
    }
  }
};

let mockWorkflows = [
  {
    workflowId: 'wf-ord-9821',
    id: 'wf-ord-9821',
    workflow: 'order-fulfillment',
    workflowName: 'order-fulfillment',
    status: 'RUNNING',
    state: 'RUNNING',
    currentStep: 'process-payment',
    dateCreated: new Date(Date.now() - 1000 * 60 * 3).toISOString(),
    dateUpdated: new Date(Date.now() - 1000 * 15).toISOString(),
    lastUpdated: new Date(Date.now() - 1000 * 15).toISOString(),
    metadata: { orderId: 'ORD-982110', customerId: 'CUST-8492', priority: 'HIGH', region: 'us-east-1' }
  },
  {
    workflowId: 'wf-ord-9820',
    id: 'wf-ord-9820',
    workflow: 'order-fulfillment',
    workflowName: 'order-fulfillment',
    status: 'SUCCESS',
    state: 'SUCCESS',
    currentStep: 'send-notifications',
    dateCreated: new Date(Date.now() - 1000 * 60 * 25).toISOString(),
    dateUpdated: new Date(Date.now() - 1000 * 60 * 22).toISOString(),
    lastUpdated: new Date(Date.now() - 1000 * 60 * 22).toISOString(),
    metadata: { orderId: 'ORD-982094', customerId: 'CUST-3312', priority: 'NORMAL', region: 'eu-west-1' }
  },
  {
    workflowId: 'wf-ord-9819',
    id: 'wf-ord-9819',
    workflow: 'order-fulfillment',
    workflowName: 'order-fulfillment',
    status: 'SUSPENDED',
    state: 'SUSPENDED',
    currentStep: 'process-payment',
    dateCreated: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
    dateUpdated: new Date(Date.now() - 1000 * 60 * 42).toISOString(),
    lastUpdated: new Date(Date.now() - 1000 * 60 * 42).toISOString(),
    metadata: { orderId: 'ORD-981971', customerId: 'CUST-1094', priority: 'HIGH', region: 'us-west-2' }
  },
  {
    workflowId: 'wf-ord-9818',
    id: 'wf-ord-9818',
    workflow: 'order-fulfillment',
    workflowName: 'order-fulfillment',
    status: 'FAILED',
    state: 'FAILED',
    currentStep: 'reserve-inventory',
    dateCreated: new Date(Date.now() - 1000 * 60 * 90).toISOString(),
    dateUpdated: new Date(Date.now() - 1000 * 60 * 88).toISOString(),
    lastUpdated: new Date(Date.now() - 1000 * 60 * 88).toISOString(),
    metadata: { orderId: 'ORD-981822', customerId: 'CUST-6501', priority: 'LOW', region: 'ap-southeast-1' }
  },
  {
    workflowId: 'wf-onb-4102',
    id: 'wf-onb-4102',
    workflow: 'customer-onboarding',
    workflowName: 'customer-onboarding',
    status: 'SUCCESS',
    state: 'SUCCESS',
    currentStep: 'send-welcome-kit',
    dateCreated: new Date(Date.now() - 1000 * 60 * 120).toISOString(),
    dateUpdated: new Date(Date.now() - 1000 * 60 * 115).toISOString(),
    lastUpdated: new Date(Date.now() - 1000 * 60 * 115).toISOString(),
    metadata: { applicantId: 'APP-41029', tier: 'ENTERPRISE', complianceOfficer: 'j.doe@org.io' }
  },
  {
    workflowId: 'wf-onb-4103',
    id: 'wf-onb-4103',
    workflow: 'customer-onboarding',
    workflowName: 'customer-onboarding',
    status: 'SUSPENDED',
    state: 'SUSPENDED',
    currentStep: 'aml-sanctions-check',
    dateCreated: new Date(Date.now() - 1000 * 60 * 35).toISOString(),
    dateUpdated: new Date(Date.now() - 1000 * 60 * 32).toISOString(),
    lastUpdated: new Date(Date.now() - 1000 * 60 * 32).toISOString(),
    metadata: { applicantId: 'APP-41031', tier: 'STANDARD', complianceOfficer: 'pending' }
  },
  {
    workflowId: 'wf-rec-1055',
    id: 'wf-rec-1055',
    workflow: 'payment-reconciliation',
    workflowName: 'payment-reconciliation',
    status: 'RUNNING',
    state: 'RUNNING',
    currentStep: 'match-transactions',
    dateCreated: new Date(Date.now() - 1000 * 60 * 10).toISOString(),
    dateUpdated: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    lastUpdated: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    metadata: { batchId: 'BATCH-20261004-01', processor: 'STRIPE_US', volumeUSD: '482590.20' }
  },
  {
    workflowId: 'wf-rec-1054',
    id: 'wf-rec-1054',
    workflow: 'payment-reconciliation',
    workflowName: 'payment-reconciliation',
    status: 'SUCCESS',
    state: 'SUCCESS',
    currentStep: 'archive-batch',
    dateCreated: new Date(Date.now() - 1000 * 60 * 360).toISOString(),
    dateUpdated: new Date(Date.now() - 1000 * 60 * 350).toISOString(),
    lastUpdated: new Date(Date.now() - 1000 * 60 * 350).toISOString(),
    metadata: { batchId: 'BATCH-20261003-02', processor: 'ADYEN_EU', volumeEUR: '912040.50' }
  }
];

const mockStepExecution = {
  'wf-ord-9821': [
    {
      workflowId: 'wf-ord-9821',
      workflow: 'order-fulfillment',
      stepName: 'validate-order',
      name: 'validate-order',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.OrderValidationStep',
      status: 'SUCCESS',
      state: 'SUCCESS',
      startTime: new Date(Date.now() - 180000).toISOString(),
      dateStarted: new Date(Date.now() - 180000).toISOString(),
      endTime: new Date(Date.now() - 179200).toISOString(),
      dateEnded: new Date(Date.now() - 179200).toISOString(),
      duration: 800,
      retryCount: 0,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED'
    },
    {
      workflowId: 'wf-ord-9821',
      workflow: 'order-fulfillment',
      stepName: 'reserve-inventory',
      name: 'reserve-inventory',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.InventoryReservationStep',
      status: 'SUCCESS',
      state: 'SUCCESS',
      startTime: new Date(Date.now() - 179000).toISOString(),
      dateStarted: new Date(Date.now() - 179000).toISOString(),
      endTime: new Date(Date.now() - 177500).toISOString(),
      dateEnded: new Date(Date.now() - 177500).toISOString(),
      duration: 1500,
      retryCount: 0,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED'
    },
    {
      workflowId: 'wf-ord-9821',
      workflow: 'order-fulfillment',
      stepName: 'process-payment',
      name: 'process-payment',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.AsyncPaymentStep',
      status: 'RUNNING',
      state: 'RUNNING',
      startTime: new Date(Date.now() - 177000).toISOString(),
      dateStarted: new Date(Date.now() - 177000).toISOString(),
      endTime: null,
      dateEnded: null,
      duration: null,
      retryCount: 1,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED',
      scheduledRetry: {
        scheduledAt: new Date(Date.now() + 45000).toISOString(),
        remainingMillis: 45000,
        type: 'EXPONENTIAL_BACKOFF',
        reason: 'Payment gateway jitter backoff retry 1 of 3',
        batchSize: 1
      }
    },
    {
      workflowId: 'wf-ord-9821',
      workflow: 'order-fulfillment',
      stepName: 'dispatch-shipping',
      name: 'dispatch-shipping',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.ShippingDispatchStep',
      status: 'PENDING',
      state: 'PENDING',
      startTime: null,
      dateStarted: null,
      endTime: null,
      dateEnded: null,
      duration: null,
      retryCount: 0,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED'
    },
    {
      workflowId: 'wf-ord-9821',
      workflow: 'order-fulfillment',
      stepName: 'send-notifications',
      name: 'send-notifications',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.CustomerNotificationStep',
      status: 'PENDING',
      state: 'PENDING',
      startTime: null,
      dateStarted: null,
      endTime: null,
      dateEnded: null,
      duration: null,
      retryCount: 0,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED'
    }
  ],
  'wf-ord-9819': [
    {
      workflowId: 'wf-ord-9819',
      workflow: 'order-fulfillment',
      stepName: 'validate-order',
      name: 'validate-order',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.OrderValidationStep',
      status: 'SUCCESS',
      state: 'SUCCESS',
      startTime: new Date(Date.now() - 2700000).toISOString(),
      dateStarted: new Date(Date.now() - 2700000).toISOString(),
      endTime: new Date(Date.now() - 2699100).toISOString(),
      dateEnded: new Date(Date.now() - 2699100).toISOString(),
      duration: 900,
      retryCount: 0,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED'
    },
    {
      workflowId: 'wf-ord-9819',
      workflow: 'order-fulfillment',
      stepName: 'reserve-inventory',
      name: 'reserve-inventory',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.InventoryReservationStep',
      status: 'SUCCESS',
      state: 'SUCCESS',
      startTime: new Date(Date.now() - 2699000).toISOString(),
      dateStarted: new Date(Date.now() - 2699000).toISOString(),
      endTime: new Date(Date.now() - 2697800).toISOString(),
      dateEnded: new Date(Date.now() - 2697800).toISOString(),
      duration: 1200,
      retryCount: 0,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED'
    },
    {
      workflowId: 'wf-ord-9819',
      workflow: 'order-fulfillment',
      stepName: 'process-payment',
      name: 'process-payment',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.AsyncPaymentStep',
      status: 'SUSPENDED',
      state: 'SUSPENDED',
      startTime: new Date(Date.now() - 2697000).toISOString(),
      dateStarted: new Date(Date.now() - 2697000).toISOString(),
      endTime: new Date(Date.now() - 2695000).toISOString(),
      dateEnded: new Date(Date.now() - 2695000).toISOString(),
      duration: 2000,
      retryCount: 3,
      circuitBreaker: 'HALF_OPEN',
      circuitBreakerState: 'HALF_OPEN',
      errorMessage: 'Payment Gateway Timeout (504): Gateway failed to respond within 3000ms after 3 retries',
      scheduledRetry: {
        scheduledAt: new Date(Date.now() + 180000).toISOString(),
        remainingMillis: 180000,
        type: 'OPERATOR_REPLAY_AWAITING',
        reason: 'Maximum automatic retry attempts (3/3) exceeded. Suspended for operator review or manual replay.',
        batchSize: 1
      }
    },
    {
      workflowId: 'wf-ord-9819',
      workflow: 'order-fulfillment',
      stepName: 'dispatch-shipping',
      name: 'dispatch-shipping',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.ShippingDispatchStep',
      status: 'SKIPPED',
      state: 'SKIPPED',
      startTime: null,
      dateStarted: null,
      endTime: null,
      dateEnded: null,
      duration: null,
      retryCount: 0,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED'
    },
    {
      workflowId: 'wf-ord-9819',
      workflow: 'order-fulfillment',
      stepName: 'send-notifications',
      name: 'send-notifications',
      typeClassName: 'com.github.orcas.orchestrator.service.steps.CustomerNotificationStep',
      status: 'SKIPPED',
      state: 'SKIPPED',
      startTime: null,
      dateStarted: null,
      endTime: null,
      dateEnded: null,
      duration: null,
      retryCount: 0,
      circuitBreaker: 'CLOSED',
      circuitBreakerState: 'CLOSED'
    }
  ]
};

const mockContexts = {
  'wf-ord-9821': {
    orderId: 'ORD-982110',
    amount: 349.99,
    currency: 'USD',
    items: [{ sku: 'PROD-MACBOOK-AIR', qty: 1, unitPrice: 349.99 }],
    customer: { id: 'CUST-8492', email: 'alex.rivera@acme.com', tier: 'PLATINUM' },
    inventoryReservationId: 'RES-883199'
  },
  'wf-ord-9819': {
    orderId: 'ORD-981971',
    amount: 1250.00,
    currency: 'USD',
    items: [{ sku: 'ENT-LICENSE-SERVER', qty: 2, unitPrice: 625.00 }],
    customer: { id: 'CUST-1094', email: 'tech-ops@enterprise.co', tier: 'HIGH' },
    inventoryReservationId: 'RES-441209',
    lastPaymentAttempt: { gateway: 'stripe_connect', failureCode: 'GATEWAY_TIMEOUT', attemptNumber: 3 }
  }
};

const mockLogs = {
  'wf-ord-9821': [
    { id: 1, timestamp: new Date(Date.now() - 179500).toISOString(), dateCreated: new Date(Date.now() - 179500).toISOString(), level: 'INFO', action: 'STATE_CHANGE', stepName: 'validate-order', message: 'Order schema validated successfully against JSON schema v2', snapshotJson: '{"status":"SUCCESS","duration":800}' },
    { id: 2, timestamp: new Date(Date.now() - 178000).toISOString(), dateCreated: new Date(Date.now() - 178000).toISOString(), level: 'INFO', action: 'STATE_CHANGE', stepName: 'reserve-inventory', message: 'Warehouse reservation confirmed. SKU PROD-MACBOOK-AIR locked in WH-NJ-1', snapshotJson: '{"status":"SUCCESS","duration":1500}' },
    { id: 3, timestamp: new Date(Date.now() - 176500).toISOString(), dateCreated: new Date(Date.now() - 176500).toISOString(), level: 'INFO', action: 'STATE_CHANGE', stepName: 'process-payment', message: 'Initiating capture authorization token with payment microservice', snapshotJson: '{"status":"RUNNING","attempt":1}' },
    { id: 4, timestamp: new Date(Date.now() - 175000).toISOString(), dateCreated: new Date(Date.now() - 175000).toISOString(), level: 'WARN', action: 'RETRY', stepName: 'process-payment', message: 'Upstream gateway delayed response, applying jitter backoff (attempt 1/3)', snapshotJson: '{"retryCount":1,"delayMillis":2000,"circuitBreaker":"CLOSED"}' }
  ],
  'wf-ord-9819': [
    { id: 10, timestamp: new Date(Date.now() - 2699500).toISOString(), dateCreated: new Date(Date.now() - 2699500).toISOString(), level: 'INFO', action: 'STATE_CHANGE', stepName: 'validate-order', message: 'Order payload validated', snapshotJson: '{"status":"SUCCESS"}' },
    { id: 11, timestamp: new Date(Date.now() - 2698000).toISOString(), dateCreated: new Date(Date.now() - 2698000).toISOString(), level: 'INFO', action: 'STATE_CHANGE', stepName: 'reserve-inventory', message: 'Inventory lock acquired: RES-441209', snapshotJson: '{"status":"SUCCESS"}' },
    { id: 12, timestamp: new Date(Date.now() - 2696500).toISOString(), dateCreated: new Date(Date.now() - 2696500).toISOString(), level: 'ERROR', action: 'RETRY', stepName: 'process-payment', message: 'Attempt 1 failed: Connection reset by peer (gateway.pay.internal)', snapshotJson: '{"attempt":1,"error":"Connection reset"}' },
    { id: 13, timestamp: new Date(Date.now() - 2695500).toISOString(), dateCreated: new Date(Date.now() - 2695500).toISOString(), level: 'ERROR', action: 'RETRY', stepName: 'process-payment', message: 'Attempt 2 failed: Read timed out after 3000ms', snapshotJson: '{"attempt":2,"error":"Timeout"}' },
    { id: 14, timestamp: new Date(Date.now() - 2695000).toISOString(), dateCreated: new Date(Date.now() - 2695000).toISOString(), level: 'FATAL', action: 'STATE_CHANGE', stepName: 'process-payment', message: 'Attempt 3 failed: Maximum retries exceeded. Circuit breaker tripped to HALF_OPEN. Workflow SUSPENDED.', snapshotJson: '{"status":"SUSPENDED","retries":3,"circuitBreakerState":"HALF_OPEN"}' }
  ]
};

const mockKafkaTopics = [
  { name: 'orchestrator.workflow.events', partitions: 12, replicationFactor: 3, retentionMs: 604800000, cleanupPolicy: 'delete', totalMessages: 1845209 },
  { name: 'orchestrator.step.executions', partitions: 12, replicationFactor: 3, retentionMs: 604800000, cleanupPolicy: 'delete', totalMessages: 5410940 },
  { name: 'orchestrator.workflow.dead-letter', partitions: 6, replicationFactor: 3, retentionMs: 2592000000, cleanupPolicy: 'delete', totalMessages: 28 },
  { name: 'commerce.orders.created', partitions: 8, replicationFactor: 3, retentionMs: 1209600000, cleanupPolicy: 'delete', totalMessages: 890214 },
  { name: 'commerce.payments.authorized', partitions: 8, replicationFactor: 3, retentionMs: 1209600000, cleanupPolicy: 'delete', totalMessages: 885102 },
  { name: 'notifications.dispatch.queue', partitions: 4, replicationFactor: 3, retentionMs: 259200000, cleanupPolicy: 'delete', totalMessages: 312040 }
];

const mockKafkaConsumerGroups = [
  { groupId: 'workflow-engine-workers', state: 'STABLE', protocolType: 'consumer', membersCount: 6, totalLag: 4, topics: ['orchestrator.workflow.events', 'orchestrator.step.executions'] },
  { groupId: 'workflow-audit-indexer', state: 'STABLE', protocolType: 'consumer', membersCount: 3, totalLag: 0, topics: ['orchestrator.workflow.events'] },
  { groupId: 'payment-fulfillment-group', state: 'STABLE', protocolType: 'consumer', membersCount: 4, totalLag: 12, topics: ['commerce.payments.authorized'] },
  { groupId: 'notification-sender-daemon', state: 'STABLE', protocolType: 'consumer', membersCount: 2, totalLag: 0, topics: ['notifications.dispatch.queue'] },
  { groupId: 'dead-letter-monitoring-agent', state: 'EMPTY', protocolType: 'consumer', membersCount: 0, totalLag: 0, topics: ['orchestrator.workflow.dead-letter'] }
];

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS, PATCH',
    'Access-Control-Allow-Headers': 'Content-Type, Accept, Authorization',
  });
  res.end(JSON.stringify(data, null, 2));
}

function parseJsonBody(req) {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        resolve({});
      }
    });
  });
}

const server = http.createServer(async (req, res) => {
  // CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS, PATCH',
      'Access-Control-Allow-Headers': 'Content-Type, Accept, Authorization',
    });
    return res.end();
  }

  const parsedUrl = parseUrl(req.url, true);
  const pathname = parsedUrl.pathname;
  const query = parsedUrl.query || {};

  // Chaos testing trigger
  if (query.simulateError) {
    const errCode = parseInt(query.simulateError, 10) || 500;
    return sendJson(res, errCode, {
      status: errCode,
      error: 'Simulated Server Error',
      message: `Chaos test requested error ${errCode} on endpoint ${pathname}`,
      timestamp: new Date().toISOString(),
      path: pathname
    });
  }

  console.log(`[Mock Server] ${req.method} ${pathname}`);

  // Base Health
  if (pathname === '/health' || pathname === '/api/health') {
    return sendJson(res, 200, { status: 'UP', service: 'workflow-orchestrator-mock-server', timestamp: new Date().toISOString() });
  }

  // Workflows List: GET /api/orchestrator/workflows
  if (pathname === '/api/orchestrator/workflows' && req.method === 'GET') {
    let filtered = [...mockWorkflows];

    const searchWf = query.workflow || query.workflowName;
    if (searchWf) {
      filtered = filtered.filter(w => (w.workflow || '').toLowerCase().includes(searchWf.toLowerCase()));
    }
    const searchId = query.workflowId || query.id;
    if (searchId) {
      filtered = filtered.filter(w => (w.workflowId || w.id || '').toLowerCase().includes(searchId.toLowerCase()));
    }
    const searchStatus = query.status || query.state;
    if (searchStatus) {
      filtered = filtered.filter(w => (w.status || w.state || '').toUpperCase() === searchStatus.toUpperCase());
    }
    const searchStep = query.stepName || query.currentStep;
    if (searchStep) {
      filtered = filtered.filter(w => (w.currentStep || '').toLowerCase().includes(searchStep.toLowerCase()));
    }

    const page = parseInt(query.page || '0', 10);
    const size = parseInt(query.size || '10', 10);
    const totalElements = filtered.length;
    const totalPages = Math.ceil(totalElements / size) || 1;
    const paginated = filtered.slice(page * size, (page + 1) * size);

    return sendJson(res, 200, {
      content: paginated,
      totalElements,
      totalPages,
      page,
      size,
      number: page,
      first: page === 0,
      last: page >= totalPages - 1
    });
  }

  // Batch Replay: POST /api/orchestrator/workflows/replay
  if (pathname === '/api/orchestrator/workflows/replay' && req.method === 'POST') {
    const body = await parseJsonBody(req);
    let count = 0;
    mockWorkflows.forEach(wf => {
      if (wf.status === 'SUSPENDED' || wf.status === 'FAILED') {
        wf.status = 'RUNNING';
        wf.state = 'RUNNING';
        wf.dateUpdated = new Date().toISOString();
        wf.lastUpdated = new Date().toISOString();
        count++;
      }
    });
    return sendJson(res, 200, {
      status: 'SUCCESS',
      message: `Retry initiated for ${count} failed or suspended workflow(s)`,
      replayedCount: count,
      replayed: count,
      matched: count,
      filtersApplied: body
    });
  }

  // Workflow Definition: GET /api/orchestrator/workflows/definitions/:name
  const defMatch = pathname.match(/^\/api\/orchestrator\/workflows\/definitions\/([^/]+)$/);
  if (defMatch && req.method === 'GET') {
    const wfName = decodeURIComponent(defMatch[1]);
    const def = mockDefinitions[wfName] || {
      workflow: wfName,
      workflowName: wfName,
      version: '1.0.0',
      routes: [],
      steps: [],
      stepConfigs: {}
    };
    return sendJson(res, 200, def);
  }

  // Single Step Replay: POST /api/orchestrator/workflows/:id/steps/:step/replay
  const stepReplayMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)\/steps\/([^/]+)\/replay$/);
  if (stepReplayMatch && req.method === 'POST') {
    const wfId = decodeURIComponent(stepReplayMatch[1]);
    const stepName = decodeURIComponent(stepReplayMatch[2]);
    const steps = mockStepExecution[wfId];
    if (steps) {
      const target = steps.find(s => s.stepName === stepName || s.name === stepName);
      if (target) {
        target.status = 'RUNNING';
        target.state = 'RUNNING';
        target.retryCount = (target.retryCount || 0) + 1;
        target.retries = target.retryCount;
        target.startTime = new Date().toISOString();
        target.dateStarted = new Date().toISOString();
        target.scheduledRetry = null;
      }
    }
    const wf = mockWorkflows.find(w => w.workflowId === wfId || w.id === wfId);
    if (wf) {
      wf.status = 'RUNNING';
      wf.state = 'RUNNING';
      wf.dateUpdated = new Date().toISOString();
      wf.lastUpdated = new Date().toISOString();
    }
    return sendJson(res, 200, {
      status: 'QUEUED',
      workflowId: wfId,
      stepName: stepName,
      message: `Replay successfully dispatched for step '${stepName}' in workflow '${wfId}'`
    });
  }

  // Single Step Detail: GET /api/orchestrator/workflows/:id/steps/:step
  const singleStepMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)\/steps\/([^/]+)$/);
  if (singleStepMatch && req.method === 'GET') {
    const wfId = decodeURIComponent(singleStepMatch[1]);
    const stepName = decodeURIComponent(singleStepMatch[2]);
    const steps = mockStepExecution[wfId] || [];
    const stepItem = steps.find(s => s.stepName === stepName || s.name === stepName);
    const wf = mockWorkflows.find(w => w.workflowId === wfId || w.id === wfId);
    const def = wf ? mockDefinitions[wf.workflow] : null;
    const config = def?.stepConfigs?.[stepName] || {
      async: false,
      retryEnabled: true,
      maxAttempts: 3,
      delayMillis: 1000,
      circuitBreakerEnabled: true,
      circuitBreakerName: `${stepName}-cb`,
      circuitBreakerState: 'CLOSED'
    };

    if (stepItem) {
      return sendJson(res, 200, {
        ...stepItem,
        stepConfig: config,
        circuitBreakerState: stepItem.circuitBreakerState || config.circuitBreakerState || 'CLOSED',
        scheduledRetry: stepItem.scheduledRetry || null
      });
    }

    return sendJson(res, 200, {
      workflowId: wfId,
      stepName,
      name: stepName,
      state: 'PENDING',
      status: 'PENDING',
      retryCount: 0,
      stepConfig: config,
      circuitBreakerState: 'CLOSED'
    });
  }

  // Single Step Context: GET /api/orchestrator/workflows/:id/steps/:step/context
  const stepCtxMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)\/steps\/([^/]+)\/context$/);
  if (stepCtxMatch && req.method === 'GET') {
    const wfId = decodeURIComponent(stepCtxMatch[1]);
    const stepName = decodeURIComponent(stepCtxMatch[2]);
    const baseCtx = mockContexts[wfId] || {};
    return sendJson(res, 200, {
      workflowId: wfId,
      stepName,
      input: { ...baseCtx, _executingStep: stepName, _executionTimestamp: new Date().toISOString() },
      output: { executionState: 'COMPLETED_SUCCESSFULLY', executionDurationMs: 840, exitCode: 0, verified: true }
    });
  }

  // Single Step Logs: GET /api/orchestrator/workflows/:id/steps/:step/logs
  const stepLogsMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)\/steps\/([^/]+)\/logs$/);
  if (stepLogsMatch && req.method === 'GET') {
    const wfId = decodeURIComponent(stepLogsMatch[1]);
    const stepName = decodeURIComponent(stepLogsMatch[2]);
    const logs = (mockLogs[wfId] || []).filter(l => l.stepName === stepName);
    return sendJson(res, 200, logs);
  }

  // Workflow Steps: GET /api/orchestrator/workflows/:id/steps
  const stepsMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)\/steps$/);
  if (stepsMatch && req.method === 'GET') {
    const wfId = decodeURIComponent(stepsMatch[1]);
    const steps = mockStepExecution[wfId] || [
      { workflowId: wfId, stepName: 'step-1', name: 'step-1', status: 'SUCCESS', state: 'SUCCESS', startTime: new Date(Date.now() - 60000).toISOString(), dateStarted: new Date(Date.now() - 60000).toISOString(), endTime: new Date(Date.now() - 58000).toISOString(), dateEnded: new Date(Date.now() - 58000).toISOString(), duration: 2000, retryCount: 0, circuitBreaker: 'CLOSED' },
      { workflowId: wfId, stepName: 'step-2', name: 'step-2', status: 'RUNNING', state: 'RUNNING', startTime: new Date(Date.now() - 57000).toISOString(), dateStarted: new Date(Date.now() - 57000).toISOString(), endTime: null, dateEnded: null, duration: null, retryCount: 1, circuitBreaker: 'CLOSED' }
    ];
    return sendJson(res, 200, steps);
  }

  // Workflow Context: GET /api/orchestrator/workflows/:id/context
  const ctxMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)\/context$/);
  if (ctxMatch && req.method === 'GET') {
    const wfId = decodeURIComponent(ctxMatch[1]);
    return sendJson(res, 200, {
      workflowId: wfId,
      context: mockContexts[wfId] || { defaultKey: 'mockValue', workflowId: wfId }
    });
  }

  // Workflow Metadata: GET /api/orchestrator/workflows/:id/metadata
  const metaMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)\/metadata$/);
  if (metaMatch && req.method === 'GET') {
    const wfId = decodeURIComponent(metaMatch[1]);
    const wf = mockWorkflows.find(w => w.workflowId === wfId || w.id === wfId);
    return sendJson(res, 200, {
      workflowId: wfId,
      values: wf?.metadata || { source: 'mock-engine', cluster: 'local-dev', environment: 'production-simulation' }
    });
  }

  // Workflow Logs: GET /api/orchestrator/workflows/:id/logs
  const logsMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)\/logs$/);
  if (logsMatch && req.method === 'GET') {
    const wfId = decodeURIComponent(logsMatch[1]);
    return sendJson(res, 200, mockLogs[wfId] || []);
  }

  // Single Workflow Instance: GET /api/orchestrator/workflows/:id
  const wfMatch = pathname.match(/^\/api\/orchestrator\/workflows\/([^/]+)$/);
  if (wfMatch && req.method === 'GET') {
    const wfId = decodeURIComponent(wfMatch[1]);
    const wf = mockWorkflows.find(w => w.workflowId === wfId || w.id === wfId);
    if (!wf) {
      return sendJson(res, 404, { status: 404, error: 'Not Found', message: `Workflow instance not found with ID '${wfId}'` });
    }
    return sendJson(res, 200, wf);
  }

  // Kafka Topics List: GET /api/orchestrator/kafka/topics
  if (pathname === '/api/orchestrator/kafka/topics' && req.method === 'GET') {
    return sendJson(res, 200, mockKafkaTopics);
  }

  // Single Kafka Topic: GET /api/orchestrator/kafka/topics/:name
  const topicMatch = pathname.match(/^\/api\/orchestrator\/kafka\/topics\/([^/]+)$/);
  if (topicMatch && req.method === 'GET') {
    const topicName = decodeURIComponent(topicMatch[1]);
    const topic = mockKafkaTopics.find(t => t.name === topicName);
    if (!topic) {
      return sendJson(res, 404, { status: 404, error: 'Not Found', message: `Kafka topic not found: '${topicName}'` });
    }
    const partitionsDetail = Array.from({ length: topic.partitions }).map((_, idx) => ({
      partition: idx,
      leader: (idx % 3) + 1,
      replicas: [1, 2, 3],
      isr: [1, 2, 3],
      earliestOffset: 0,
      latestOffset: Math.floor(topic.totalMessages / topic.partitions) + Math.floor(Math.random() * 50)
    }));
    return sendJson(res, 200, {
      ...topic,
      configs: { 'cleanup.policy': topic.cleanupPolicy, 'retention.ms': topic.retentionMs, 'min.insync.replicas': '2' },
      partitionsDetail
    });
  }

  // Kafka Consumer Groups: GET /api/orchestrator/kafka/consumer-groups
  if (pathname === '/api/orchestrator/kafka/consumer-groups' && req.method === 'GET') {
    return sendJson(res, 200, mockKafkaConsumerGroups);
  }

  // Single Kafka Consumer Group: GET /api/orchestrator/kafka/consumer-groups/:id
  const groupMatch = pathname.match(/^\/api\/orchestrator\/kafka\/consumer-groups\/([^/]+)$/);
  if (groupMatch && req.method === 'GET') {
    const groupId = decodeURIComponent(groupMatch[1]);
    const group = mockKafkaConsumerGroups.find(g => g.groupId === groupId);
    if (!group) {
      return sendJson(res, 404, { status: 404, error: 'Not Found', message: `Consumer group not found: '${groupId}'` });
    }
    const assignments = group.topics.flatMap(topic => [
      { topic, partition: 0, currentOffset: 124010, logEndOffset: 124010, lag: 0, consumerId: 'worker-pod-1-0a8f' },
      { topic, partition: 1, currentOffset: 98112, logEndOffset: 98114, lag: 2, consumerId: 'worker-pod-2-7b1c' },
      { topic, partition: 2, currentOffset: 110430, logEndOffset: 110432, lag: 2, consumerId: 'worker-pod-3-3d9e' }
    ]);
    return sendJson(res, 200, {
      ...group,
      coordinator: { id: 2, host: 'kafka-broker-2.internal', port: 9092 },
      members: [
        { memberId: 'worker-pod-1-0a8f', clientId: 'workflow-worker-1', host: '/10.244.1.42' },
        { memberId: 'worker-pod-2-7b1c', clientId: 'workflow-worker-2', host: '/10.244.2.19' },
        { memberId: 'worker-pod-3-3d9e', clientId: 'workflow-worker-3', host: '/10.244.3.88' }
      ],
      partitionAssignments: assignments
    });
  }

  // Fallback 404
  return sendJson(res, 404, {
    status: 404,
    error: 'Endpoint Not Found',
    message: `No mock route matched for ${req.method} ${pathname}`,
    availableRoutes: [
      'GET /api/orchestrator/workflows',
      'GET /api/orchestrator/workflows/:id',
      'GET /api/orchestrator/workflows/definitions/:name',
      'GET /api/orchestrator/workflows/:id/steps',
      'GET /api/orchestrator/workflows/:id/steps/:step',
      'GET /api/orchestrator/workflows/:id/steps/:step/context',
      'GET /api/orchestrator/workflows/:id/steps/:step/logs',
      'POST /api/orchestrator/workflows/:id/steps/:step/replay',
      'POST /api/orchestrator/workflows/replay',
      'GET /api/orchestrator/kafka/topics',
      'GET /api/orchestrator/kafka/topics/:name',
      'GET /api/orchestrator/kafka/consumer-groups',
      'GET /api/orchestrator/kafka/consumer-groups/:id'
    ]
  });
});

server.listen(PORT, HOST, () => {
  console.log(`\n======================================================`);
  console.log(`🚀 Workflow Orchestrator Mock Server running!`);
  console.log(`📡 URL: http://${HOST === '0.0.0.0' ? 'localhost' : HOST}:${PORT}`);
  console.log(`⚙️  API Base: http://localhost:${PORT}/api/orchestrator`);
  console.log(`======================================================\n`);
});

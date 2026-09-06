<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import {
  daysRemaining,
  expiryColor,
  expiryText,
  edgeColor,
  edgeText,
  outcomeColor,
  sortCertificates
} from '../utils/certStatus.js'
import { useAuth } from '@trevorism/ui-auth'

const { isAdmin, isAuthenticated, ready, login } = useAuth()

const signedOut = ref(false)
const administrator = isAdmin
const certificates = ref([])
const verifications = ref({})
const loading = ref(true)
const error = ref('')
const confirming = ref(null)
const rotatingId = ref(null)
const lastRun = ref(null)
const sortKey = ref(null)
const sortDirection = ref('asc')
const verifyBatch = ref(0)

const columns = [
  { key: 'category', label: 'Category' },
  { key: 'wildcard', label: 'Wildcard' },
  { key: 'gcpProject', label: 'Project' },
  { key: 'expiry', label: 'Expires in' },
  { key: 'edge', label: 'Edge' },
  { key: 'outcome', label: 'Last rotation' }
]

const rows = computed(() =>
  sortCertificates(certificates.value, {
    key: sortKey.value,
    direction: sortDirection.value,
    verifications: verifications.value
  })
)

const sortSummary = computed(() => {
  const column = columns.find((candidate) => candidate.key === sortKey.value)
  if (!column) return 'Sorted by urgency'
  const order = sortDirection.value === 'asc' ? 'ascending' : 'descending'
  return `Sorted by ${column.label.toLowerCase()}, ${order}`
})

function toggleSort(key) {
  if (sortKey.value !== key) {
    sortKey.value = key
    sortDirection.value = 'asc'
    return
  }
  if (sortDirection.value === 'asc') {
    sortDirection.value = 'desc'
    return
  }
  sortKey.value = null
  sortDirection.value = 'asc'
}

function ariaSort(key) {
  if (sortKey.value !== key) return 'none'
  return sortDirection.value === 'asc' ? 'ascending' : 'descending'
}

function sortIndicator(key) {
  if (sortKey.value !== key) return '↕'
  return sortDirection.value === 'asc' ? '↑' : '↓'
}

function isUnauthorized(e) {
  return e?.response?.status === 401
}

function isForbidden(e) {
  return e?.response?.status === 403
}

function showSignedOut() {
  signedOut.value = true
  loading.value = false
  error.value = ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get('api/certificate')
    certificates.value = data
    verifyAll()
  } catch (e) {
    if (isUnauthorized(e)) {
      showSignedOut()
      return
    }
    error.value = isForbidden(e)
      ? 'Your account does not have access to certificates.'
      : 'Unable to load certificates. Please try again.'
  } finally {
    loading.value = false
  }
}

function verifyAll() {
  const batch = ++verifyBatch.value
  verifications.value = {}
  certificates.value.forEach((certificate) => {
    axios
      .get(`api/certificate/${certificate.id}/verify`)
      .then(({ data }) => {
        recordVerification(batch, certificate.id, data)
      })
      .catch((e) => {
        if (isUnauthorized(e)) {
          showSignedOut()
          return
        }
        recordVerification(batch, certificate.id, {
          probeFailed: true,
          detail: 'the verification request did not complete'
        })
      })
  })
}

function recordVerification(batch, certificateId, verification) {
  if (batch !== verifyBatch.value) return
  verifications.value = { ...verifications.value, [certificateId]: verification }
}

async function rotate() {
  const certificate = confirming.value
  confirming.value = null
  rotatingId.value = certificate.id
  lastRun.value = null
  try {
    const { data } = await axios.post(`api/certificate/${certificate.id}/rotate`, {
      acmeServer: 'production'
    })
    lastRun.value = data
    await load()
  } catch (e) {
    if (isUnauthorized(e)) {
      showSignedOut()
      return
    }
    error.value = isForbidden(e)
      ? `Your account is not allowed to rotate ${certificate.wildcard}.`
      : `Rotation of ${certificate.wildcard} could not be started.`
  } finally {
    rotatingId.value = null
  }
}

function daysFor(certificate) {
  return daysRemaining(certificate.notAfter)
}

onMounted(async () => {
  await ready
  if (!isAuthenticated.value) {
    showSignedOut()
    return
  }
  load()
})
</script>

<template>
  <div class="cert-table">
    <div class="cert-table__header">
      <div>
        <h2 class="cert-table__title">Wildcard certificates</h2>
        <p v-if="!signedOut" class="cert-table__caption">{{ sortSummary }}</p>
      </div>
      <va-button v-if="!signedOut" preset="secondary" :disabled="loading" @click="load">
        Refresh
      </va-button>
    </div>

    <div v-if="signedOut" class="signing-in">
      <p>Sign in to see the wildcard certificates Trevorism manages.</p>
      <va-button class="mt-4" @click="login()">Sign in</va-button>
    </div>

    <template v-else>
    <va-alert v-if="error" color="danger" class="mb-4">{{ error }}</va-alert>

    <va-alert v-if="lastRun" :color="outcomeColor(lastRun.outcome)" class="mb-4">
      {{ lastRun.wildcard }}: {{ lastRun.outcome }}
      <span v-if="lastRun.failureDetail"> — {{ lastRun.failureDetail }}</span>
    </va-alert>

    <va-inner-loading :loading="loading">
      <div class="cert-table__surface">
        <table>
          <thead>
            <tr>
              <th
                v-for="column in columns"
                :key="column.key"
                scope="col"
                :aria-sort="ariaSort(column.key)"
                :class="{ 'is-sorted': sortKey === column.key }"
              >
                <button type="button" class="cert-table__sort" @click="toggleSort(column.key)">
                  {{ column.label }}
                  <span class="cert-table__arrow">{{ sortIndicator(column.key) }}</span>
                </button>
              </th>
              <th class="cert-table__actions"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="certificate in rows" :key="certificate.id">
              <td>{{ certificate.category }}</td>
              <td class="cert-table__wildcard">{{ certificate.wildcard }}</td>
              <td class="cert-table__muted">{{ certificate.gcpProject }}</td>
              <td>
                <va-badge :color="expiryColor(daysFor(certificate))" :text="expiryText(daysFor(certificate))" />
              </td>
              <td>
                <va-badge
                  :color="edgeColor(verifications[certificate.id])"
                  :text="edgeText(verifications[certificate.id])"
                />
              </td>
              <td>
                <va-badge
                  v-if="certificate.lastOutcome"
                  :color="outcomeColor(certificate.lastOutcome)"
                  :text="certificate.lastOutcome"
                />
                <span v-else class="cert-table__muted">never</span>
              </td>
              <td class="cert-table__actions">
                <va-button
                  v-if="administrator"
                  size="small"
                  :loading="rotatingId === certificate.id"
                  :disabled="rotatingId !== null"
                  @click="confirming = certificate"
                >
                  Rotate
                </va-button>
              </td>
            </tr>
          </tbody>
        </table>

        <p v-if="!loading && !error && rows.length === 0" class="cert-table__empty">
          No certificates are being tracked yet.
        </p>
      </div>
    </va-inner-loading>

    <va-modal
      :model-value="confirming !== null"
      title="Rotate this certificate?"
      ok-text="Rotate"
      @ok="rotate"
      @cancel="confirming = null"
    >
      <p v-if="confirming">
        This issues a new production certificate for {{ confirming.wildcard }} and replaces it on
        {{ confirming.gcpProject }}. It takes about 90 seconds, and the edge can take several hours to
        catch up.
      </p>
      <p v-if="confirming" class="cert-table__muted">
        Certificates with more than 30 days left are left alone and the run reports SKIPPED.
      </p>
    </va-modal>
    </template>
  </div>
</template>

<style scoped>
.cert-table {
  max-width: 1120px;
  margin: 0 auto;
  padding: 1.75rem 1.25rem 3rem;
}

.cert-table__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.cert-table__title {
  margin: 0;
  font-size: 1.4rem;
  line-height: 1.2;
}

.cert-table__caption {
  margin: 0.3rem 0 0;
  font-size: 0.8rem;
  color: var(--va-secondary, #64748b);
}

.cert-table__surface {
  border: 1px solid var(--va-background-border, #e2e8f0);
  border-radius: 12px;
  background: var(--va-background-secondary, #ffffff);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.07);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

th,
td {
  padding: 0.95rem 1.35rem;
  text-align: left;
  vertical-align: middle;
}

thead th {
  padding-top: 0.7rem;
  padding-bottom: 0.7rem;
  background: var(--va-background-element, #f8fafc);
  border-bottom: 1px solid var(--va-background-border, #e2e8f0);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--va-secondary, #64748b);
}

thead th.is-sorted {
  color: var(--va-primary, #154ec1);
}

tbody td {
  border-top: 1px solid var(--va-background-border, #eef2f6);
}

tbody tr:first-child td {
  border-top: none;
}

tbody tr:hover td {
  background: var(--va-background-element, #f8fafc);
}

.cert-table__sort {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  letter-spacing: inherit;
  text-transform: inherit;
  color: inherit;
  cursor: pointer;
}

.cert-table__sort:hover {
  color: var(--va-primary, #154ec1);
}

.cert-table__sort:focus-visible {
  outline: 2px solid var(--va-primary, #154ec1);
  outline-offset: 3px;
  border-radius: 4px;
}

.cert-table__arrow {
  width: 0.7rem;
  font-size: 0.75rem;
  opacity: 0;
  transition: opacity 0.12s ease-in-out;
}

.cert-table__sort:hover .cert-table__arrow {
  opacity: 0.5;
}

thead th.is-sorted .cert-table__arrow {
  opacity: 1;
}

.cert-table__wildcard {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.88rem;
}

.cert-table__muted {
  color: var(--va-secondary, #64748b);
}

.cert-table__actions {
  width: 1%;
  white-space: nowrap;
  text-align: right;
}

.cert-table__empty {
  margin: 0;
  padding: 2.5rem 1.35rem;
  text-align: center;
  color: var(--va-secondary, #64748b);
}
</style>

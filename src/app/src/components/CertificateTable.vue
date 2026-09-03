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
  sortByUrgency
} from '../utils/certStatus.js'
import { isLoggedIn, looksLikeAdministrator, loginUrlFor } from '../utils/auth.js'

const authenticated = ref(isLoggedIn())
const administrator = ref(looksLikeAdministrator())
const certificates = ref([])
const verifications = ref({})
const loading = ref(true)
const error = ref('')
const confirming = ref(null)
const rotatingId = ref(null)
const lastRun = ref(null)

const rows = computed(() => sortByUrgency(certificates.value))
const currentUrl = computed(() => (typeof window === 'undefined' ? '' : window.location.href))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await axios.get('api/certificate')
    certificates.value = data
    verifyAll()
  } catch (e) {
    error.value = 'Unable to load certificates. You may need to sign in.'
  } finally {
    loading.value = false
  }
}

function verifyAll() {
  verifications.value = {}
  certificates.value.forEach((certificate) => {
    axios
      .get(`api/certificate/${certificate.id}/verify`)
      .then(({ data }) => {
        verifications.value = { ...verifications.value, [certificate.id]: data }
      })
      .catch(() => {
        verifications.value = { ...verifications.value, [certificate.id]: { matches: false } }
      })
  })
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
    error.value = `Rotation of ${certificate.wildcard} could not be started.`
  } finally {
    rotatingId.value = null
  }
}

function daysFor(certificate) {
  return daysRemaining(certificate.notAfter)
}

onMounted(() => {
  if (!authenticated.value) {
    loading.value = false
    return
  }
  load()
})
</script>

<template>
  <div class="cert-table">
    <div class="cert-table__header">
      <h2>Wildcard certificates</h2>
      <va-button v-if="authenticated" preset="secondary" :disabled="loading" @click="load">
        Refresh
      </va-button>
    </div>

    <div v-if="!authenticated" class="signed-out">
      <p>Please log in to view the platform's wildcard certificates.</p>
      <a :href="loginUrlFor(currentUrl)">Sign in</a>
    </div>

    <template v-else>
    <va-alert v-if="error" color="danger" class="mb-4">{{ error }}</va-alert>

    <va-alert v-if="lastRun" :color="outcomeColor(lastRun.outcome)" class="mb-4">
      {{ lastRun.wildcard }}: {{ lastRun.outcome }}
      <span v-if="lastRun.failureDetail"> — {{ lastRun.failureDetail }}</span>
    </va-alert>

    <va-inner-loading :loading="loading">
      <table class="va-table va-table--hoverable">
        <thead>
          <tr>
            <th>Category</th>
            <th>Wildcard</th>
            <th>Project</th>
            <th>Expires in</th>
            <th>Edge</th>
            <th>Last rotation</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="certificate in rows" :key="certificate.id">
            <td>{{ certificate.category }}</td>
            <td>{{ certificate.wildcard }}</td>
            <td>{{ certificate.gcpProject }}</td>
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
              <span v-else>never</span>
            </td>
            <td>
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
        {{ confirming.gcpProject }}. It takes about 90 seconds, and the edge can take an hour to catch up.
      </p>
    </va-modal>
    </template>
  </div>
</template>

<style scoped>
.cert-table__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
}
</style>

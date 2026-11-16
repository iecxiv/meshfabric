package iecxiv.meshfabric.ui.login

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import iecxiv.meshfabric.data.remote.FidoWebOriginPolicy

internal class FidoRegistrationPage(
    private val policy: FidoWebOriginPolicy,
    options: JsonObject,
    private val ceremonyId: String
) {
    sealed class Delivery {
        data class Credential(val value: JsonObject): Delivery()
        object Failed: Delivery()
    }
    private val optionsJson = options.toString()
    private var started = false
    private var terminal = false
    val hasStarted: Boolean get() = started
    init { require(ceremonyId.matches(Regex("[A-Za-z0-9_-]{16,128}"))) }
    fun invalidate() { terminal = true }
    fun receive(sourceOrigin: String, mainFrame: Boolean, currentUrl: String, message: String): Delivery? {
        if (!started || terminal || message.length > 1_000_000) return null
        if (!policy.allowsMessage(sourceOrigin, mainFrame) || !policy.allowsNavigation(currentUrl)) return null
        val payload = runCatching { JsonParser.parseString(message).asJsonObject }.getOrNull() ?: return null
        val id = payload.get("ceremonyId") ?: return null
        if (!id.isJsonPrimitive || !id.asJsonPrimitive.isString || id.asString != ceremonyId) return null
        val error = payload.get("error")
        val credential = payload.get("credential")
        val delivery = when {
            error != null && credential == null && error.isJsonPrimitive && error.asJsonPrimitive.isBoolean && error.asBoolean -> Delivery.Failed
            error == null && credential != null && credential.isJsonObject -> Delivery.Credential(credential.asJsonObject.deepCopy())
            else -> return null
        }
        terminal = true
        return delivery
    }
    fun begin(assertion:Boolean=false): String? {
        if (started || terminal) return null
        started = true
        val optionsLiteral = JsonPrimitive(optionsJson).toString()
        val idLiteral = JsonPrimitive(ceremonyId).toString()
        return """
            (() => {
              'use strict';
              const bridge = window.meshFabricFidoRegistration;
              if (!bridge || typeof bridge.postMessage !== 'function') return;
              const ceremonyId = $idLiteral;
              const assertionMode = $assertion;
              const decode = value => {
                if (typeof value !== 'string' || !value.length) throw new Error('Invalid binary field');
                const raw = value.replace(/-/g, '+').replace(/_/g, '/');
                return Uint8Array.from(atob(raw + '='.repeat((4 - raw.length % 4) % 4)), c => c.charCodeAt(0));
              };
              const encode = value => {
                if (!(value instanceof ArrayBuffer)) throw new Error('Invalid credential buffer');
                const bytes = new Uint8Array(value);
                let raw = '';
                for (let i = 0; i < bytes.length; i++) raw += String.fromCharCode(bytes[i]);
                return btoa(raw).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
              };
              const deliver = payload => bridge.postMessage(JSON.stringify({ceremonyId, ...payload}));
              (async () => {
                try {
                  const publicKey = JSON.parse($optionsLiteral);
                  publicKey.challenge = decode(publicKey.challenge);
                  if (!assertionMode) publicKey.user.id = decode(publicKey.user.id);
                  if (assertionMode) {
                    if (!Array.isArray(publicKey.allowCredentials)) throw new Error('Invalid credentials');
                    publicKey.allowCredentials = publicKey.allowCredentials.map(item => ({...item, id: decode(item.id)}));
                  }
                  if (publicKey.excludeCredentials !== undefined) {
                    if (!Array.isArray(publicKey.excludeCredentials)) throw new Error('Invalid exclusions');
                    publicKey.excludeCredentials = publicKey.excludeCredentials.map(item => ({...item, id: decode(item.id)}));
                  }
                  const c = assertionMode ? await navigator.credentials.get({publicKey}) : await navigator.credentials.create({publicKey});
                  if (!c || c.type !== 'public-key') throw new Error('No credential');
                  deliver({credential: {
                    id: c.id, rawId: encode(c.rawId), type: c.type,
                    response: assertionMode ? {
                      clientDataJSON: encode(c.response.clientDataJSON),
                      authenticatorData: encode(c.response.authenticatorData),
                      signature: encode(c.response.signature),
                      userHandle: c.response.userHandle == null ? null : encode(c.response.userHandle)
                    } : {
                      clientDataJSON: encode(c.response.clientDataJSON),
                      attestationObject: encode(c.response.attestationObject)
                    }
                  }});
                } catch (_) { deliver({error: true}); }
              })();
            })();
        """.trimIndent()
    }
}

/* ============================================================
   MAIN.JS — Shortener Hub Logic
   ============================================================ */
class URLShortener {
    constructor() {
        this.form        = document.getElementById('shortenForm');
        this.urlInput    = document.getElementById('urlInput');
        this.aliasToggle = document.getElementById('customAliasToggle');
        this.aliasWrapper= document.getElementById('aliasInputWrapper');
        this.aliasInput  = document.getElementById('aliasInput');
        this.shortenBtn  = document.getElementById('shortenBtn');
        this.btnText     = document.getElementById('btnText');

        this.loadingState  = document.getElementById('loadingState');
        this.errorState    = document.getElementById('errorState');
        this.errorMessage  = document.getElementById('errorMessage');
        this.resultSection = document.getElementById('resultSection');
        this.shortenedUrl  = document.getElementById('shortenedUrl');
        this.copyBtn       = document.getElementById('copyBtn');
        this.analyticsLink = document.getElementById('analyticsLink');

        // Quick stats
        this.viewAnalyticsBtn  = document.getElementById('viewAnalyticsBtn');

        this.currentShortCode = '';
        this.init();
    }

    init() {
        this.form.addEventListener('submit', e => this.handleSubmit(e));
        this.copyBtn.addEventListener('click', () => this.handleCopy());
        this.aliasToggle.addEventListener('change', () => this.toggleAlias());
        this.urlInput.addEventListener('input', () => this.resetStates());
    }

    toggleAlias() {
        if (this.aliasToggle.checked) {
            this.aliasWrapper.style.display = 'flex';
            this.aliasInput.focus();
        } else {
            this.aliasWrapper.style.display = 'none';
            this.aliasInput.value = '';
        }
    }

    resetStates() {
        this.loadingState.style.display  = 'none';
        this.errorState.style.display    = 'none';
        this.resultSection.style.display = 'none';
    }

    setLoading(on) {
        this.loadingState.style.display = on ? 'flex' : 'none';
        this.shortenBtn.disabled = on;
        if (this.btnText) this.btnText.textContent = on ? 'FORGING...' : 'SHORTEN URL';
    }

    showError(msg) {
        this.errorMessage.textContent = msg;
        this.errorState.style.display = 'flex';
    }

    async handleSubmit(e) {
        e.preventDefault();
        this.resetStates();

        const url = this.urlInput.value.trim();
        if (!url) return this.showError('Please enter a URL.');
        if (!this.isValidUrl(url)) return this.showError('Please include http:// or https:// in the URL.');

        const alias = this.aliasToggle.checked ? this.aliasInput.value.trim() : '';
        if (alias && !this.isValidAlias(alias)) {
            return this.showError('Alias must be 3–20 alphanumeric chars, hyphens, or underscores.');
        }

        this.setLoading(true);

        try {
            const fd = new FormData();
            fd.append('url', url);
            if (alias) fd.append('alias', alias);

            const res = await fetch('/api/shorten', { method: 'POST', body: fd });
            if (!res.ok) {
                let txt = await res.text();
                try {
                    const json = JSON.parse(txt);
                    if (json.message) txt = json.message;
                    else if (json.error) txt = json.error;
                } catch (e) {
                    // response is not JSON, keep txt
                }
                if (res.status === 409) throw new Error('Alias already taken. Try another.');
                throw new Error(txt || `Server error (${res.status})`);
            }

            const shortUrl = await res.text();
            this.currentShortCode = shortUrl.split('/').pop();
            this.shortenedUrl.value = shortUrl;

            if (this.analyticsLink) {
                this.analyticsLink.href = `analytics.html?code=${this.currentShortCode}`;
            }
            if (this.viewAnalyticsBtn) {
                this.viewAnalyticsBtn.href = `analytics.html?code=${this.currentShortCode}`;
            }

            this.resultSection.style.display = 'block';



        } catch (err) {
            this.showError(err.message || 'Something went wrong. Please try again.');
        } finally {
            this.setLoading(false);
        }
    }

    async handleCopy() {
        const val = this.shortenedUrl.value;
        if (!val) return;
        try {
            await navigator.clipboard.writeText(val);
        } catch (_) {
            this.shortenedUrl.select();
            document.execCommand('copy');
        }
        this.copyBtn.textContent = 'Copied!';
        this.copyBtn.classList.add('copied');
        setTimeout(() => {
            this.copyBtn.textContent = 'Copy';
            this.copyBtn.classList.remove('copied');
        }, 2000);
    }


    isValidUrl(s) {
        try { const u = new URL(s); return u.protocol === 'http:' || u.protocol === 'https:'; }
        catch (_) { return false; }
    }

    isValidAlias(a) { return /^[a-zA-Z0-9-_]{3,20}$/.test(a); }
}

document.addEventListener('DOMContentLoaded', () => new URLShortener());

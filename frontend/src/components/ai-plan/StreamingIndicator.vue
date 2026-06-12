<template>
  <div class="streaming-indicator">
    <span class="dot" />
    <span class="dot" />
    <span class="dot" />
  </div>
</template>

<style scoped>
.streaming-indicator {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  width: fit-content;
  margin: 0 auto;
  padding: 10px 14px;
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(17, 24, 39, 0.055);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-red-light);
  animation: pulse 1.2s ease-in-out infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

:root[data-theme="dark"] .streaming-indicator {
  background: var(--color-card);
  border-color: var(--color-border);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.3);
}

@keyframes pulse {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>

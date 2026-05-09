/**
 * SSE POST stream composable.
 * Uses fetch() + ReadableStream because EventSource only supports GET.
 */

export function useSSE() {
  /**
   * @param {string} url
   * @param {FormData} formData
   * @param {(event: {type: string, content: string, metadata: any}) => void} onEvent
   * @param {() => void} onDone
   * @param {(err: Error) => void} onError
   * @returns {AbortController} caller can call abort() to cancel
   */
  function streamPost(url, formData, onEvent, onDone, onError) {
    const controller = new AbortController()

    ;(async () => {
      try {
        const resp = await fetch(url, {
          method: 'POST',
          body: formData,
          signal: controller.signal,
        })

        if (!resp.ok) {
          const text = await resp.text()
          onError(new Error(`HTTP ${resp.status}: ${text}`))
          return
        }

        const reader = resp.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() // keep incomplete line in buffer

          let currentEvent = ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim()
              if (currentEvent === 'done' || data === '[DONE]') {
                onDone()
                return
              }
              try {
                const parsed = JSON.parse(data)
                onEvent({
                  type: parsed.type || 'unknown',
                  content: parsed.content || '',
                  metadata: parsed.metadata || null,
                })
              } catch {
                onEvent({ type: 'unknown', content: data, metadata: null })
              }
              currentEvent = ''
            } else if (line.trim() === '') {
              currentEvent = ''
            }
          }
        }

        onDone()
      } catch (err) {
        if (err.name !== 'AbortError') {
          onError(err)
        }
      }
    })()

    return controller
  }

  return { streamPost }
}

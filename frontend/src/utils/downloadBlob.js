/**
 * Triggers a browser download for a Blob via a throwaway anchor element.
 * Shared by any page that downloads a generated file (e.g. docker-compose.yml export).
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

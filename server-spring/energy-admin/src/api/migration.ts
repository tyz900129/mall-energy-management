import http from './http'

export interface MigrationManifest {
  formatVersion: number
  systemName: string
  applicationVersion: string
  schemaVersion: string
  createdAt: string
  createdBy: string
  databaseBytes: number
  uploadFileCount: number
  uploadBytes: number
  recordCounts: Record<string, number>
}

export interface MigrationJob {
  id: string
  type: 'EXPORT' | 'IMPORT'
  status: string
  progress: number
  message: string
  error?: string
  fileName?: string
  downloadToken?: string
  preview?: MigrationManifest
  safetyBackup?: string
  createdAt: string
  updatedAt: string
}

export const migrationApi = {
  startExport: (password: string) => http.post<any, MigrationJob>('/api/v1/system/migration/exports', { password }),
  job: (id: string) => http.get<any, MigrationJob>(`/api/v1/system/migration/jobs/${id}`),
  initializeUpload: (file: File) => http.post<any, { uploadId: string; chunkBytes: number; receivedBytes: number; totalBytes: number; complete: boolean }>(
    '/api/v1/system/migration/imports/uploads', { fileName: file.name, fileSize: file.size }
  ),
  uploadChunk: (uploadId: string, index: number, chunk: Blob, onProgress?: (value: number) => void) =>
    http.put(`/api/v1/system/migration/imports/uploads/${uploadId}/chunks/${index}`, chunk, {
      headers: { 'Content-Type': 'application/octet-stream' },
      timeout: 180000,
      onUploadProgress: event => { if (event.total) onProgress?.(event.loaded / event.total) }
    }),
  validate: (uploadId: string, password: string) => http.post<any, MigrationJob>(
    `/api/v1/system/migration/imports/uploads/${uploadId}/validate`, { password }
  ),
  restore: (jobId: string, confirmation: string) => http.post<any, MigrationJob>(
    `/api/v1/system/migration/imports/${jobId}/restore`, { confirmation }
  ),
  downloadUrl: (job: MigrationJob) => `/api/v1/system/migration/exports/${job.id}/download?token=${encodeURIComponent(job.downloadToken || '')}`
}

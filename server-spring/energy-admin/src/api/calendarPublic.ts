import request from './http'

export const calendarPublicApi = {
  detail: (token: string) => request.get(`/api/v1/calendar-public/tasks/${token}`),
  submit: (token: string, body: any) => request.post(`/api/v1/calendar-public/tasks/${token}/submit`, body),
  upload: (token: string, file: File, name: string) => {
    const form = new FormData()
    form.append('file', file)
    if (name) form.append('name', name)
    return request.post(`/api/v1/calendar-public/tasks/${token}/photos`, form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  photoUrl: (token: string, photoId: number) => `/api/v1/calendar-public/tasks/${token}/photos/${photoId}`
}

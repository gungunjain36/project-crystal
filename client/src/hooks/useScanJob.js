import { useQuery } from '@tanstack/react-query'
import { getResult } from '../api/scans'

export function useScanJob(jobId) {
  return useQuery({
    queryKey: ['job', jobId],
    queryFn: () => getResult(jobId),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (status === 'success' || status === 'failure' || status === 'completed' || status === 'failed') {
        return false
      }
      return 3000
    },
    retry: 2,
  })
}

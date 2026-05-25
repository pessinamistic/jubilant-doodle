import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { HomePage } from './pages/HomePage'
import { InstancesPage } from './pages/InstancesPage'
import { InstanceDetailPage } from './pages/InstanceDetailPage'
import './index.css'

export default function App() {
  return (
    <BrowserRouter>
      <Toaster
        position="top-right"
        toastOptions={{
          duration: 4000,
          style: {
            background: '#1c2333',
            color: '#e5e7eb',
            border: '1px solid rgba(255,255,255,0.08)',
          },
        }}
      />
      <Routes>
        <Route path="/"              element={<HomePage />} />
        <Route path="/instances"     element={<InstancesPage />} />
        <Route path="/instances/:id" element={<InstanceDetailPage />} />
      </Routes>
    </BrowserRouter>
  )
}

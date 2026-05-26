import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { HomePage } from './pages/HomePage'
import { InstancesPage } from './pages/InstancesPage'
import { InstanceDetailPage } from './pages/InstanceDetailPage'
import { DeployPage } from './pages/DeployPage'
import { ImageManagementPage } from './pages/ImageManagementPage'
import { ImageToolPage } from './pages/ImageToolPage'
import './index.css'

export default function App() {
  return (
    <BrowserRouter>
      <Toaster
        position="top-right"
        toastOptions={{
          duration: 4000,
          style: {
            background: 'var(--bg-surface)',
            color: 'var(--text-primary)',
            border: '2px solid var(--border-strong)',
            borderRadius: '4px',
            boxShadow: 'var(--shadow-raised)',
            fontFamily: 'var(--font-sans)',
            fontWeight: 600,
          },
        }}
      />
      <Routes>
        <Route path="/"              element={<HomePage />} />
        <Route path="/instances"     element={<InstancesPage />} />
        <Route path="/instances/:id" element={<InstanceDetailPage />} />
        <Route path="/deploy"        element={<DeployPage />} />
        <Route path="/images"        element={<ImageManagementPage />} />
        <Route path="/images/:dbType" element={<ImageToolPage />} />
      </Routes>
    </BrowserRouter>
  )
}

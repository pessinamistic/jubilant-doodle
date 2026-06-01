import { useState } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { HomePage } from './pages/HomePage'
import { InstancesPage } from './pages/InstancesPage'
import { InstanceDetailPage } from './pages/InstanceDetailPage'
import { DeployPage } from './pages/DeployPage'
import { ConfigurationsPage } from './pages/ConfigurationsPage'
import { ConfigurationFormPage } from './pages/ConfigurationFormPage'
import { ImageManagementPage } from './pages/ImageManagementPage'
import { ImageToolPage } from './pages/ImageToolPage'
import { DashboardPage } from './pages/DashboardPage'
import { SplashScreen } from './components/SplashScreen'
import { WelcomeWizard } from './components/WelcomeWizard'
import { useUserProfile } from './hooks/useUserProfile'
import './index.css'

export default function App() {
  const [splashDone, setSplashDone] = useState(false)
  const { profile, save } = useUserProfile()

  if (!splashDone) {
    return <SplashScreen onDone={() => setSplashDone(true)} />
  }

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
        <Route path="/instances/:id"           element={<InstanceDetailPage />} />
        <Route path="/deploy"                   element={<DeployPage />} />
        <Route path="/configurations"           element={<ConfigurationsPage />} />
        <Route path="/configurations/new"       element={<ConfigurationFormPage />} />
        <Route path="/configurations/:id/edit" element={<ConfigurationFormPage />} />
        <Route path="/images"                  element={<ImageManagementPage />} />
        <Route path="/images/:dbType" element={<ImageToolPage />} />
        <Route path="/dashboard"              element={<DashboardPage />} />
      </Routes>
      {!profile && <WelcomeWizard onComplete={save} />}
    </BrowserRouter>
  )
}

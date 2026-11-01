import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.rentpilot.app',
  appName: 'RentPilot',
  webDir: 'dist',
  backgroundColor: '#ffffff',
  plugins: {
    SystemBars: { insetsHandling: 'css', style: 'LIGHT', hidden: false },
    Keyboard: { resizeOnFullScreen: true },
  },
}
export default config

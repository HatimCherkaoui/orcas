import { createRoot } from 'react-dom/client';
import './styles.css';
import App from './DashboardApp';
import { ThemeProvider } from './hooks/useTheme';

createRoot(document.getElementById('root')).render(<ThemeProvider><App /></ThemeProvider>);

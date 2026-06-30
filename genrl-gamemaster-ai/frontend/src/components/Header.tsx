import React from 'react';

interface HeaderProps {
  activeView: string;
  onNavigate: (view: 'dashboard' | 'new-experiment') => void;
}

const Header: React.FC<HeaderProps> = ({ activeView, onNavigate }) => {
  return (
    <header
      style={{
        background: 'var(--nbp-navy)',
        borderBottom: '3px solid var(--nbp-gold)',
        position: 'sticky',
        top: 0,
        zIndex: 100,
      }}
    >
      <div
        style={{
          maxWidth: 1280,
          margin: '0 auto',
          padding: '0 1.5rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          minHeight: 70,
        }}
      >
        {/* Brand */}
        <div>
          <div
            style={{
              fontFamily: 'var(--font-heading)',
              fontSize: '1.4rem',
              fontWeight: 700,
              color: 'var(--nbp-gold)',
              letterSpacing: '0.01em',
            }}
          >
            GenRL GameMaster AI
          </div>
          <div
            style={{
              fontSize: '0.72rem',
              color: 'rgba(255,255,255,0.65)',
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              marginTop: 2,
            }}
          >
            System Ewolucji Algorytmicznej
          </div>
        </div>

        {/* Navigation */}
        <nav style={{ display: 'flex', gap: '0.5rem' }}>
          <NavButton
            label="Dashboard"
            active={activeView === 'dashboard'}
            onClick={() => onNavigate('dashboard')}
          />
          <NavButton
            label="Nowy Eksperyment"
            active={activeView === 'new-experiment'}
            onClick={() => onNavigate('new-experiment')}
          />
        </nav>
      </div>
    </header>
  );
};

interface NavButtonProps {
  label: string;
  active: boolean;
  onClick: () => void;
}

const NavButton: React.FC<NavButtonProps> = ({ label, active, onClick }) => (
  <button
    onClick={onClick}
    style={{
      background: active ? 'var(--nbp-gold)' : 'transparent',
      color: active ? 'var(--nbp-navy)' : 'rgba(255,255,255,0.85)',
      border: `1.5px solid ${active ? 'var(--nbp-gold)' : 'rgba(255,255,255,0.3)'}`,
      borderRadius: 'var(--radius-md)',
      padding: '0.45rem 1rem',
      font: '600 0.875rem var(--font-body)',
      cursor: 'pointer',
      transition: 'all var(--transition)',
    }}
    onMouseEnter={(e) => {
      if (!active) {
        (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--nbp-gold)';
        (e.currentTarget as HTMLButtonElement).style.color = 'var(--nbp-gold)';
      }
    }}
    onMouseLeave={(e) => {
      if (!active) {
        (e.currentTarget as HTMLButtonElement).style.borderColor = 'rgba(255,255,255,0.3)';
        (e.currentTarget as HTMLButtonElement).style.color = 'rgba(255,255,255,0.85)';
      }
    }}
  >
    {label}
  </button>
);

export default Header;

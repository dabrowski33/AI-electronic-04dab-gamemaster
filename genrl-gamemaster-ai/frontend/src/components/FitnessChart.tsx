import React from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import type { FitnessPoint } from '../types';

interface FitnessChartProps {
  data: FitnessPoint[];
}

const FitnessChart: React.FC<FitnessChartProps> = ({ data }) => {
  if (data.length === 0) {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: 280,
          color: 'var(--nbp-text-muted)',
          fontFamily: 'var(--font-heading)',
          fontSize: '1rem',
        }}
      >
        Brak danych do wyświetlenia
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart
        data={data}
        margin={{ top: 10, right: 20, left: 0, bottom: 5 }}
      >
        <CartesianGrid strokeDasharray="3 3" stroke="#e8e3da" />
        <XAxis
          dataKey="generation"
          tick={{ fontFamily: 'Arial, sans-serif', fontSize: 11, fill: '#5a5a5a' }}
          label={{
            value: 'Generacja',
            position: 'insideBottom',
            offset: -3,
            style: { fontFamily: 'Arial, sans-serif', fontSize: 11, fill: '#5a5a5a' },
          }}
        />
        <YAxis
          tick={{ fontFamily: 'Arial, sans-serif', fontSize: 11, fill: '#5a5a5a' }}
          label={{
            value: 'Fitness',
            angle: -90,
            position: 'insideLeft',
            style: { fontFamily: 'Arial, sans-serif', fontSize: 11, fill: '#5a5a5a' },
          }}
          domain={[0, 'auto']}
        />
        <Tooltip
          contentStyle={{
            fontFamily: 'Arial, sans-serif',
            fontSize: '0.82rem',
            background: '#002C5B',
            color: '#fff',
            border: '1px solid #B59A57',
            borderRadius: 6,
          }}
          labelFormatter={(v) => `Generacja ${v}`}
          formatter={(value: number, name: string) => [
            value.toFixed(4),
            name === 'best_fitness' ? 'Najlepszy fitness' : 'Średni fitness',
          ]}
        />
        <Legend
          formatter={(v) => (v === 'best_fitness' ? 'Najlepszy fitness' : 'Średni fitness')}
          wrapperStyle={{ fontFamily: 'Arial, sans-serif', fontSize: '0.82rem' }}
        />
        <Line
          type="monotone"
          dataKey="best_fitness"
          stroke="#B59A57"
          strokeWidth={2.5}
          dot={false}
          activeDot={{ r: 5, fill: '#B59A57' }}
          isAnimationActive={false}
        />
        <Line
          type="monotone"
          dataKey="avg_fitness"
          stroke="#002C5B"
          strokeWidth={2}
          strokeDasharray="4 2"
          dot={false}
          activeDot={{ r: 4, fill: '#002C5B' }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
};

export default FitnessChart;

import { Brain, Code2, Lightbulb, Boxes, Sparkles, Zap, Cpu, Rocket, GitBranch, Flame, Wind, Star, Gem, Beaker, MessageSquare, BookOpen, Palette, Clock3 } from 'lucide-react'

// Model family themes with icons and colors - diverse palette
export const FAMILY_THEMES = {
  // Meta
  'llama3.2': { icon: Zap, color: '#FF6B35', bgColor: 'rgba(255, 107, 53, 0.12)', borderColor: 'rgba(255, 107, 53, 0.4)' },
  'llama2': { icon: Zap, color: '#FF6B35', bgColor: 'rgba(255, 107, 53, 0.12)', borderColor: 'rgba(255, 107, 53, 0.4)' },
  'llama': { icon: Zap, color: '#FF6B35', bgColor: 'rgba(255, 107, 53, 0.12)', borderColor: 'rgba(255, 107, 53, 0.4)' },

  // Google
  'gemma': { icon: Sparkles, color: '#7C3AED', bgColor: 'rgba(124, 58, 237, 0.12)', borderColor: 'rgba(124, 58, 237, 0.4)' },
  'gemma3': { icon: Sparkles, color: '#7C3AED', bgColor: 'rgba(124, 58, 237, 0.12)', borderColor: 'rgba(124, 58, 237, 0.4)' },

  // Mistral AI
  'mistral': { icon: Lightbulb, color: '#F59E0B', bgColor: 'rgba(245, 158, 11, 0.12)', borderColor: 'rgba(245, 158, 11, 0.4)' },
  'mixtral': { icon: GitBranch, color: '#14B8A6', bgColor: 'rgba(20, 184, 166, 0.12)', borderColor: 'rgba(20, 184, 166, 0.4)' },

  // Microsoft
  'phi': { icon: Code2, color: '#10B981', bgColor: 'rgba(16, 185, 129, 0.12)', borderColor: 'rgba(16, 185, 129, 0.4)' },

  // Open source/community
  'openchat': { icon: MessageSquare, color: '#06B6D4', bgColor: 'rgba(6, 182, 212, 0.12)', borderColor: 'rgba(6, 182, 212, 0.4)' },
  'neural-chat': { icon: Brain, color: '#EC4899', bgColor: 'rgba(236, 72, 153, 0.12)', borderColor: 'rgba(236, 72, 153, 0.4)' },
  'dolphin': { icon: Boxes, color: '#8B5CF6', bgColor: 'rgba(139, 92, 246, 0.12)', borderColor: 'rgba(139, 92, 246, 0.4)' },
  'orca': { icon: Wind, color: '#0EA5E9', bgColor: 'rgba(14, 165, 233, 0.12)', borderColor: 'rgba(14, 165, 233, 0.4)' },
  'vicuna': { icon: Brain, color: '#A855F7', bgColor: 'rgba(168, 85, 247, 0.12)', borderColor: 'rgba(168, 85, 247, 0.4)' },
  'alpaca': { icon: Sparkles, color: '#F43F5E', bgColor: 'rgba(244, 63, 94, 0.12)', borderColor: 'rgba(244, 63, 94, 0.4)' },
  'koala': { icon: Brain, color: '#06D6A0', bgColor: 'rgba(6, 214, 160, 0.12)', borderColor: 'rgba(6, 214, 160, 0.4)' },
  'hermes': { icon: Rocket, color: '#7C2D12', bgColor: 'rgba(124, 45, 18, 0.12)', borderColor: 'rgba(124, 45, 18, 0.4)' },
  'nous': { icon: Brain, color: '#DC2626', bgColor: 'rgba(220, 38, 38, 0.12)', borderColor: 'rgba(220, 38, 38, 0.4)' },
  'nousresearch': { icon: Brain, color: '#DC2626', bgColor: 'rgba(220, 38, 38, 0.12)', borderColor: 'rgba(220, 38, 38, 0.4)' },
  'nous-hermes': { icon: Rocket, color: '#9D174D', bgColor: 'rgba(157, 23, 77, 0.12)', borderColor: 'rgba(157, 23, 77, 0.4)' },

  // Chinese models
  'deepseek': { icon: Rocket, color: '#EF4444', bgColor: 'rgba(239, 68, 68, 0.12)', borderColor: 'rgba(239, 68, 68, 0.4)' },
  'qwen': { icon: Cpu, color: '#3B82F6', bgColor: 'rgba(59, 130, 246, 0.12)', borderColor: 'rgba(59, 130, 246, 0.4)' },
  'yi': { icon: Flame, color: '#F97316', bgColor: 'rgba(249, 115, 22, 0.12)', borderColor: 'rgba(249, 115, 22, 0.4)' },

  // Others
  'claude': { icon: Brain, color: '#1D4ED8', bgColor: 'rgba(29, 78, 216, 0.12)', borderColor: 'rgba(29, 78, 216, 0.4)' },
  'command': { icon: Lightbulb, color: '#EA580C', bgColor: 'rgba(234, 88, 12, 0.12)', borderColor: 'rgba(234, 88, 12, 0.4)' },
  'starling': { icon: Star, color: '#D946EF', bgColor: 'rgba(217, 70, 239, 0.12)', borderColor: 'rgba(217, 70, 239, 0.4)' },
  'falcon': { icon: Rocket, color: '#CA8A04', bgColor: 'rgba(202, 138, 4, 0.12)', borderColor: 'rgba(202, 138, 4, 0.4)' },
  'solar': { icon: Flame, color: '#EAB308', bgColor: 'rgba(234, 179, 8, 0.12)', borderColor: 'rgba(234, 179, 8, 0.4)' },
  'tinyllama': { icon: Code2, color: '#6366F1', bgColor: 'rgba(99, 102, 241, 0.12)', borderColor: 'rgba(99, 102, 241, 0.4)' },
  'stablelm': { icon: Gem, color: '#84CC16', bgColor: 'rgba(132, 204, 22, 0.12)', borderColor: 'rgba(132, 204, 22, 0.4)' },
  'mpt': { icon: Cpu, color: '#0891B2', bgColor: 'rgba(8, 145, 178, 0.12)', borderColor: 'rgba(8, 145, 178, 0.4)' },
  'bagel': { icon: Beaker, color: '#EC4899', bgColor: 'rgba(236, 72, 153, 0.12)', borderColor: 'rgba(236, 72, 153, 0.4)' },
  'chronos': { icon: Clock3, color: '#6D28D9', bgColor: 'rgba(109, 40, 217, 0.12)', borderColor: 'rgba(109, 40, 217, 0.4)' },
  'mathstral': { icon: BookOpen, color: '#1E40AF', bgColor: 'rgba(30, 64, 175, 0.12)', borderColor: 'rgba(30, 64, 175, 0.4)' },
  'zephyr': { icon: Wind, color: '#065F46', bgColor: 'rgba(6, 95, 70, 0.12)', borderColor: 'rgba(6, 95, 70, 0.4)' },
  'dbrx': { icon: Cpu, color: '#7F1D1D', bgColor: 'rgba(127, 29, 29, 0.12)', borderColor: 'rgba(127, 29, 29, 0.4)' },
  'codestral': { icon: Code2, color: '#2563EB', bgColor: 'rgba(37, 99, 235, 0.12)', borderColor: 'rgba(37, 99, 235, 0.4)' },
  'neural': { icon: Brain, color: '#9333EA', bgColor: 'rgba(147, 51, 234, 0.12)', borderColor: 'rgba(147, 51, 234, 0.4)' },
  'miqu': { icon: Sparkles, color: '#059669', bgColor: 'rgba(5, 150, 105, 0.12)', borderColor: 'rgba(5, 150, 105, 0.4)' },
  'saiga': { icon: Palette, color: '#BE185D', bgColor: 'rgba(190, 24, 93, 0.12)', borderColor: 'rgba(190, 24, 93, 0.4)' },
  'openhermes': { icon: Brain, color: '#B45309', bgColor: 'rgba(180, 83, 9, 0.12)', borderColor: 'rgba(180, 83, 9, 0.4)' },
  'beluga': { icon: Wind, color: '#1F2937', bgColor: 'rgba(31, 41, 55, 0.12)', borderColor: 'rgba(31, 41, 55, 0.4)' },
  'nous-mix': { icon: Brain, color: '#831843', bgColor: 'rgba(131, 24, 67, 0.12)', borderColor: 'rgba(131, 24, 67, 0.4)' },
  'wizard': { icon: Sparkles, color: '#4C1D95', bgColor: 'rgba(76, 29, 149, 0.12)', borderColor: 'rgba(76, 29, 149, 0.4)' },
  'airoboros': { icon: Rocket, color: '#92400E', bgColor: 'rgba(146, 64, 14, 0.12)', borderColor: 'rgba(146, 64, 14, 0.4)' },
  'mythomax': { icon: Star, color: '#7C2D12', bgColor: 'rgba(124, 45, 18, 0.12)', borderColor: 'rgba(124, 45, 18, 0.4)' },

  // Embedding models
  'mxbai': { icon: Gem, color: '#D97706', bgColor: 'rgba(217, 119, 6, 0.12)', borderColor: 'rgba(217, 119, 6, 0.4)' },
  'mxbai-embed': { icon: Gem, color: '#D97706', bgColor: 'rgba(217, 119, 6, 0.12)', borderColor: 'rgba(217, 119, 6, 0.4)' },
  'nomic-embed': { icon: Gem, color: '#16A34A', bgColor: 'rgba(22, 163, 74, 0.12)', borderColor: 'rgba(22, 163, 74, 0.4)' },
  'nomic': { icon: Gem, color: '#16A34A', bgColor: 'rgba(22, 163, 74, 0.12)', borderColor: 'rgba(22, 163, 74, 0.4)' },
  'e5': { icon: Gem, color: '#0D9488', bgColor: 'rgba(13, 148, 136, 0.12)', borderColor: 'rgba(13, 148, 136, 0.4)' },
  'bge': { icon: Gem, color: '#7C3AED', bgColor: 'rgba(124, 58, 237, 0.12)', borderColor: 'rgba(124, 58, 237, 0.4)' },
  'jina': { icon: Gem, color: '#EC4899', bgColor: 'rgba(236, 72, 153, 0.12)', borderColor: 'rgba(236, 72, 153, 0.4)' },

  // Additional models
  'metavoice': { icon: MessageSquare, color: '#5B21B6', bgColor: 'rgba(91, 33, 182, 0.12)', borderColor: 'rgba(91, 33, 182, 0.4)' },
  'llava': { icon: Brain, color: '#EF6820', bgColor: 'rgba(239, 104, 32, 0.12)', borderColor: 'rgba(239, 104, 32, 0.4)' },
  'cogvlm': { icon: Brain, color: '#7E1E8B', bgColor: 'rgba(126, 30, 139, 0.12)', borderColor: 'rgba(126, 30, 139, 0.4)' },
  'bakllava': { icon: Brain, color: '#EAB308', bgColor: 'rgba(234, 179, 8, 0.12)', borderColor: 'rgba(234, 179, 8, 0.4)' },
  'moondream': { icon: Star, color: '#6366F1', bgColor: 'rgba(99, 102, 241, 0.12)', borderColor: 'rgba(99, 102, 241, 0.4)' },
  'antml': { icon: Code2, color: '#2563EB', bgColor: 'rgba(37, 99, 235, 0.12)', borderColor: 'rgba(37, 99, 235, 0.4)' },
  'granite': { icon: Cpu, color: '#4F46E5', bgColor: 'rgba(79, 70, 229, 0.12)', borderColor: 'rgba(79, 70, 229, 0.4)' },
  'sqlcoder': { icon: Code2, color: '#1E293B', bgColor: 'rgba(30, 41, 59, 0.12)', borderColor: 'rgba(30, 41, 59, 0.4)' },
  'codeup': { icon: Code2, color: '#6B21A8', bgColor: 'rgba(107, 33, 168, 0.12)', borderColor: 'rgba(107, 33, 168, 0.4)' },
  'starcoder': { icon: Rocket, color: '#059669', bgColor: 'rgba(5, 150, 105, 0.12)', borderColor: 'rgba(5, 150, 105, 0.4)' },
  'stable-code': { icon: Code2, color: '#DC2626', bgColor: 'rgba(220, 38, 38, 0.12)', borderColor: 'rgba(220, 38, 38, 0.4)' },
  'codeqwen': { icon: Code2, color: '#EA580C', bgColor: 'rgba(234, 88, 12, 0.12)', borderColor: 'rgba(234, 88, 12, 0.4)' },
  'internlm': { icon: Brain, color: '#2DD4BF', bgColor: 'rgba(45, 212, 191, 0.12)', borderColor: 'rgba(45, 212, 191, 0.4)' },
  'glm': { icon: Brain, color: '#7F1D1D', bgColor: 'rgba(127, 29, 29, 0.12)', borderColor: 'rgba(127, 29, 29, 0.4)' },
  'baichuan': { icon: Brain, color: '#0284C7', bgColor: 'rgba(2, 132, 199, 0.12)', borderColor: 'rgba(2, 132, 199, 0.4)' },
  'orion': { icon: Star, color: '#EA580C', bgColor: 'rgba(234, 88, 12, 0.12)', borderColor: 'rgba(234, 88, 12, 0.4)' },
  'aquila': { icon: Wind, color: '#22D3EE', bgColor: 'rgba(34, 211, 238, 0.12)', borderColor: 'rgba(34, 211, 238, 0.4)' },
}

export function getThemeForFamily(family) {
  const familyLower = family.toLowerCase()
  // Try direct match first
  if (FAMILY_THEMES[familyLower]) return FAMILY_THEMES[familyLower]
  // Try prefix match
  for (const [key, theme] of Object.entries(FAMILY_THEMES)) {
    if (familyLower.includes(key) || key.includes(familyLower.split(':')[0])) {
      return theme
    }
  }
  // Default theme
  return { icon: Sparkles, color: '#6366F1', bgColor: 'rgba(99, 102, 241, 0.12)', borderColor: 'rgba(99, 102, 241, 0.4)' }
}

// Status colors - keep existing status indicator colors
export const STATUS_COLORS = {
  running: '#22c55e',
  downloading: '#3b82f6',
  onDisk: '#9CA3AF',
}

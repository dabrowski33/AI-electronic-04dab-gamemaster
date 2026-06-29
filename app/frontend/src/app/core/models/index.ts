export type CaseType = 'REKLAMACJA' | 'ZWROT';

export type EquipmentCategory =
  | 'SMARTFONY_I_TELEFONY'
  | 'LAPTOPY_I_KOMPUTERY'
  | 'TABLETY'
  | 'TELEWIZORY_I_MONITORY'
  | 'AUDIO'
  | 'KONSOLE_I_GAMING'
  | 'SMARTWATCHE_I_WEARABLES'
  | 'APARATY_I_FOTOGRAFIA'
  | 'MALE_AGD'
  | 'AKCESORIA'
  | 'INNE';

export const EQUIPMENT_CATEGORY_LABELS: Record<EquipmentCategory, string> = {
  SMARTFONY_I_TELEFONY: 'Smartfony i telefony',
  LAPTOPY_I_KOMPUTERY: 'Laptopy i komputery',
  TABLETY: 'Tablety',
  TELEWIZORY_I_MONITORY: 'Telewizory i monitory',
  AUDIO: 'Audio (słuchawki, głośniki)',
  KONSOLE_I_GAMING: 'Konsole i gaming',
  SMARTWATCHE_I_WEARABLES: 'Smartwatche i wearables',
  APARATY_I_FOTOGRAFIA: 'Aparaty i fotografia',
  MALE_AGD: 'Małe AGD',
  AKCESORIA: 'Akcesoria (ładowarki, kable, etui)',
  INNE: 'Inne',
};

export type DecisionCategory =
  | 'ELIGIBLE'
  | 'NOT_ELIGIBLE'
  | 'NEEDS_HUMAN_REVIEW'
  | 'MORE_INFO_REQUIRED';

export const DECISION_CATEGORY_LABELS: Record<DecisionCategory, string> = {
  ELIGIBLE: 'Kwalifikuje się',
  NOT_ELIGIBLE: 'Nie kwalifikuje się',
  NEEDS_HUMAN_REVIEW: 'Wymaga weryfikacji przez konsultanta',
  MORE_INFO_REQUIRED: 'Wymagane dodatkowe informacje',
};

export interface DecisionDto {
  category: DecisionCategory;
  justification: string;
  nextSteps: string;
  missingInfo?: string[];
}

export interface CaseSummaryDto {
  type: CaseType;
  category: EquipmentCategory;
  model: string;
  purchaseDate: string;
}

export interface SubmitCaseResponse {
  sessionId: string;
  decision: DecisionDto;
  firstMessage: string;
  caseSummary: CaseSummaryDto;
}

export interface ChatMessage {
  role: 'assistant' | 'user';
  content: string;
  streaming?: boolean;
}

export interface ApiError {
  code: string;
  message: string;
  fields?: Record<string, string>;
}

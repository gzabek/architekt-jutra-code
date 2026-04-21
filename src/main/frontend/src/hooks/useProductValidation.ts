import { useState } from "react";
import { validateProduct } from "../api/products";
import type { ValidationResult } from "../api/products";

interface UseProductValidationReturn {
  validationResult: ValidationResult | null;
  isValidating: boolean;
  validate: () => Promise<void>;
}

export function useProductValidation(productId: number): UseProductValidationReturn {
  const [validationResult, setValidationResult] = useState<ValidationResult | null>(null);
  const [isValidating, setIsValidating] = useState(false);

  const validate = async (): Promise<void> => {
    setIsValidating(true);
    try {
      const result = await validateProduct(productId);
      setValidationResult(result);
    } catch {
      // Silently swallow error — preserve last good result, show nothing new
    } finally {
      setIsValidating(false);
    }
  };

  return { validationResult, isValidating, validate };
}

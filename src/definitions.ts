export interface CapacitorYoogaPosPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}

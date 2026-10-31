import 'package:flutter/material.dart';

/// JONDEU design tokens — mirrors web/tailwind.config.ts so both clients share one visual language.
class T {
  static const navy = Color(0xFF183B50);
  static const blue = Color(0xFF2477D4);
  static const orange = Color(0xFFE99545);
  static const canvas = Color(0xFFF7F5F0);
  static const surface = Color(0xFFFFFFFF);
  static const ink = Color(0xFF202A32);
  static const inkSoft = Color(0xFF77818A);
  static const line = Color(0xFFE8E4DC);
  static const success = Color(0xFF36966B);
  static const warning = Color(0xFFE8A044);
  static const danger = Color(0xFFD9534F);
  static const r = 16.0;

  static ThemeData theme() {
    final base = ColorScheme.fromSeed(seedColor: blue, primary: blue, secondary: orange, surface: surface, error: danger);
    return ThemeData(
      useMaterial3: true,
      colorScheme: base,
      scaffoldBackgroundColor: canvas,
      appBarTheme: const AppBarTheme(backgroundColor: canvas, foregroundColor: ink, elevation: 0, centerTitle: false, scrolledUnderElevation: 0,
          titleTextStyle: TextStyle(color: ink, fontSize: 20, fontWeight: FontWeight.w700)),
      cardTheme: CardThemeData(color: surface, elevation: 0, margin: EdgeInsets.zero, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(r), side: const BorderSide(color: line))),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)), textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16))),
      outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(46), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)), side: const BorderSide(color: line))),
      inputDecorationTheme: InputDecorationTheme(
        filled: true, fillColor: surface, contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: line)),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: line)),
        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: blue, width: 1.6)),
      ),
      navigationBarTheme: NavigationBarThemeData(backgroundColor: surface, indicatorColor: blue.withValues(alpha: .12), labelTextStyle: WidgetStatePropertyAll(const TextStyle(fontSize: 11, fontWeight: FontWeight.w600))),
      chipTheme: ChipThemeData(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)), side: const BorderSide(color: line)),
    );
  }

  static Color statusColor(String s) => switch (s) {
        'COMPLETED' || 'APPROVED' || 'PAID' || 'DELIVERED' || 'PASSED' || 'ACCEPTED' => success,
        'IN_PROGRESS' || 'ORDERED' || 'SUBMITTED' || 'CONTRACTOR_SELECTED' => blue,
        'DELAYED' || 'PARTIALLY_DELIVERED' || 'PROPOSALS_RECEIVED' || 'NEEDS_IMPROVEMENT' => orange,
        'FAILED' || 'REJECTED' || 'CANCELLED' || 'BLOCKED' => danger,
        'PENDING' || 'REQUESTED' || 'UNDER_REVIEW' || 'CHANGES_REQUESTED' => warning,
        _ => inkSoft,
      };
}

import 'package:flutter/material.dart';
import '../services/call_channel.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _channel = CallChannel();
  final _formKey = GlobalKey<FormState>();
  bool _saving = false;

  // Context fields sent to the AI via updateContext
  final _nameController = TextEditingController();
  final _roleController = TextEditingController();
  final _languageController = TextEditingController();
  final _notesController = TextEditingController();

  @override
  void dispose() {
    _nameController.dispose();
    _roleController.dispose();
    _languageController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);

    final context = {
      'name': _nameController.text.trim(),
      'role': _roleController.text.trim(),
      'language': _languageController.text.trim(),
      'notes': _notesController.text.trim(),
    }..removeWhere((_, v) => v.isEmpty);

    final ok = await _channel.updateContext(context);

    if (!mounted) return;
    setState(() => _saving = false);

    ScaffoldMessenger.of(this.context).showSnackBar(
      SnackBar(
        content: Text(ok ? 'Context saved.' : 'Failed to save context.'),
        backgroundColor: ok
            ? Theme.of(this.context).colorScheme.primary
            : Theme.of(this.context).colorScheme.error,
      ),
    );

    if (ok) Navigator.of(this.context).pop();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _SectionHeader('Your Identity'),
            const SizedBox(height: 8),
            _Field(
              controller: _nameController,
              label: 'Name',
              hint: 'e.g. Sarah',
              icon: Icons.person_outline,
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _roleController,
              label: 'Role / Occupation',
              hint: 'e.g. Product Manager',
              icon: Icons.work_outline,
            ),
            const SizedBox(height: 24),
            _SectionHeader('AI Preferences'),
            const SizedBox(height: 8),
            _Field(
              controller: _languageController,
              label: 'Preferred language',
              hint: 'e.g. English',
              icon: Icons.language,
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _notesController,
              label: 'Additional context',
              hint: 'Anything the AI should know about your calls…',
              icon: Icons.notes,
              maxLines: 4,
            ),
            const SizedBox(height: 32),
            FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: _saving
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.check),
              label: Text(_saving ? 'Saving…' : 'Save'),
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Text(
      title,
      style: Theme.of(context).textTheme.labelLarge?.copyWith(
            color: Theme.of(context).colorScheme.primary,
            letterSpacing: 0.5,
          ),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field({
    required this.controller,
    required this.label,
    required this.hint,
    required this.icon,
    this.maxLines = 1,
  });

  final TextEditingController controller;
  final String label;
  final String hint;
  final IconData icon;
  final int maxLines;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      maxLines: maxLines,
      textCapitalization: TextCapitalization.sentences,
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        prefixIcon: maxLines == 1 ? Icon(icon) : null,
        border: const OutlineInputBorder(),
        alignLabelWithHint: maxLines > 1,
      ),
    );
  }
}

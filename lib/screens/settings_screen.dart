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

  // Identity
  final _nameController = TextEditingController();
  final _languageController = TextEditingController();

  // Delivery address
  final _flatNumberController = TextEditingController();
  final _buildingNameController = TextEditingController();
  final _landmarkController = TextEditingController();
  final _addressController = TextEditingController();
  final _entryCodeController = TextEditingController();

  // Extra
  final _notesController = TextEditingController();

  @override
  void dispose() {
    _nameController.dispose();
    _languageController.dispose();
    _flatNumberController.dispose();
    _buildingNameController.dispose();
    _landmarkController.dispose();
    _addressController.dispose();
    _entryCodeController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);

    final ctx = {
      'name':         _nameController.text.trim(),
      'language':     _languageController.text.trim(),
      'flatNumber':   _flatNumberController.text.trim(),
      'buildingName': _buildingNameController.text.trim(),
      'landmark':     _landmarkController.text.trim(),
      'address':      _addressController.text.trim(),
      'entryCode':    _entryCodeController.text.trim(),
      'notes':        _notesController.text.trim(),
    }..removeWhere((_, v) => v.isEmpty);

    final ok = await _channel.updateContext(ctx);

    if (!mounted) return;
    setState(() => _saving = false);

    ScaffoldMessenger.of(this.context).showSnackBar(
      SnackBar(
        content: Text(ok ? 'Settings saved.' : 'Failed to save settings.'),
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
      appBar: AppBar(title: const Text('Settings')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // ── Identity ──────────────────────────────────────────────────
            _SectionHeader('Your Identity'),
            const SizedBox(height: 8),
            _Field(
              controller: _nameController,
              label: 'Your name',
              hint: 'e.g. Rahul',
              icon: Icons.person_outline,
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _languageController,
              label: 'Preferred language',
              hint: 'e.g. Hindi, Telugu, English',
              icon: Icons.language,
            ),

            // ── Delivery address ──────────────────────────────────────────
            const SizedBox(height: 24),
            _SectionHeader('Delivery Address'),
            const _SectionSubtitle(
              'HiRobin gives this to delivery riders automatically.',
            ),
            const SizedBox(height: 8),
            _Field(
              controller: _flatNumberController,
              label: 'Flat / unit number',
              hint: 'e.g. 4B, 302',
              icon: Icons.door_front_door_outlined,
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _buildingNameController,
              label: 'Building / society name',
              hint: 'e.g. Prestige Oak, Green Valley Apts',
              icon: Icons.apartment_outlined,
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _landmarkController,
              label: 'Landmark',
              hint: 'e.g. next to SBI ATM, opp. Big Bazaar',
              icon: Icons.place_outlined,
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _addressController,
              label: 'Full address (optional)',
              hint: 'Street, area, city, PIN',
              icon: Icons.map_outlined,
              maxLines: 2,
            ),
            const SizedBox(height: 12),
            _Field(
              controller: _entryCodeController,
              label: 'Gate / entry code',
              hint: 'e.g. 1234#  (leave blank if none)',
              icon: Icons.lock_outline,
            ),

            // ── Extra instructions ────────────────────────────────────────
            const SizedBox(height: 24),
            _SectionHeader('Extra Instructions'),
            const _SectionSubtitle(
              'Anything else HiRobin should know — e.g. "Do not accept cold-calls", '
              '"Always take messages from Dr. Mehta\'s clinic".',
            ),
            const SizedBox(height: 8),
            _Field(
              controller: _notesController,
              label: 'Notes for HiRobin',
              hint: 'Free-form instructions…',
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
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }
}

// ── Shared widgets ────────────────────────────────────────────────────────────

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

class _SectionSubtitle extends StatelessWidget {
  const _SectionSubtitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 2, bottom: 4),
      child: Text(
        text,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
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

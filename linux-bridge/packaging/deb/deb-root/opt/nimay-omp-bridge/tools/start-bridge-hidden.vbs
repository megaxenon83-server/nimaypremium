' Starter broen uten konsollvindu, med vanlige brukerrettigheter.
'
' Inno Setup kjorer som administrator. Startes broen direkte derfra, arver den
' administratorrettigheter - og telefonen ville da styrt OMP som admin. Derfor
' starter installasjonen denne filen via explorer.exe, som kjorer den som
' innlogget bruker (medium integritet).
Option Explicit

Dim fso, shell, here, script, command
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")

here = fso.GetParentFolderName(WScript.ScriptFullName)
script = fso.BuildPath(here, "start-bridge-hidden.ps1")

If Not fso.FileExists(script) Then
  WScript.Quit 1
End If

command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & script & """"
shell.Run command, 0, False

const
  ChileanModURL =
    'https://github.com/tonikelope/coronapoker/releases/download/v{#AppVersion}/chilean_mod.zip';

var
  ModFilePage: TInputFileWizardPage;
  ModPasswordPage: TInputQueryWizardPage;
  ModDownloadPage: TDownloadWizardPage;
  ModExtractPage: TExtractionWizardPage;
  ModZipPath, ModStage, ModPreparedPath, ModIncoming, ModBackup: String;
  ModInfo: TModZip;
  ModAttempt: Integer;

procedure InitializeWizard;
begin
  ModFilePage := CreateInputFilePage(wpSelectTasks, CustomMessage('ModLocal'),
    CustomMessage('ModFileDescription'), CustomMessage('ModFileInstructions'));
  ModFilePage.Add(CustomMessage('ModFileLabel'), 'ZIP (*.zip)|*.zip', '.zip');
  ModPasswordPage := CreateInputQueryPage(ModFilePage.ID,
    CustomMessage('ModPasswordTitle'), CustomMessage('ModPasswordDescription'),
    CustomMessage('ModPasswordInstructions'));
  ModPasswordPage.Add(CustomMessage('ModPasswordLabel'), True);
  ModDownloadPage := CreateDownloadPage(CustomMessage('ModChilean'),
    CustomMessage('ModDownloading'), nil);
  ModDownloadPage.ShowBaseNameInsteadOfUrl := True;
  ModExtractPage := CreateExtractionPage('MODs',
    CustomMessage('ModExtracting'), nil);
  ModExtractPage.ShowArchiveInsteadOfFile := False;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  if PageID = ModFilePage.ID then
    Result := WizardSilent or not WizardIsTaskSelected('mods\local')
  else if PageID = ModPasswordPage.ID then
    Result := WizardSilent or (ModZipPath = '') or not ModInfo.Encrypted;
end;

procedure DownloadChileanMod;
begin
  ModDownloadPage.Clear;
  ModDownloadPage.Add(ChileanModURL, 'chilean_mod.zip', '{#ChileanModSha256}');
  ModDownloadPage.Show;
  try
    ModDownloadPage.Download;
  finally
    ModDownloadPage.Hide;
  end;
  ModZipPath := ExpandConstant('{tmp}\chilean_mod.zip');
end;

procedure ExtractSelectedMod(const Password: String);
var
  Stage, Candidate: String;
  FreeBytes, TotalBytes: Int64;
begin
  Inc(ModAttempt);
  Stage := ExpandConstant('{tmp}\mod-stage-') + IntToStr(ModAttempt);
  if GetSpaceOnDisk64(ExpandConstant('{tmp}'), FreeBytes, TotalBytes) and
    (FreeBytes < ModInfo.ExpandedBytes) then
    RaiseException(CustomMessage('ModNoSpace'));
  try
    ModExtractPage.Clear;
    ModExtractPage.AddEx(ModZipPath, Stage, Password, True);
    ModExtractPage.Show;
    try
      ModExtractPage.Extract;
    finally
      ModExtractPage.Clear;
      ModExtractPage.Hide;
    end;
    Candidate := Stage;
    if ModInfo.Wrapped then
      Candidate := Candidate + '\mod';
    RequireModZip(FileExists(Candidate + '\mod.xml'));
    ModStage := Stage;
    ModPreparedPath := Candidate;
  except
    DelTree(Stage, True, True, True);
    RaiseException(GetExceptionMessage);
  end;
end;

procedure ClearPreparedMod;
begin
  if ModStage <> '' then
    DelTree(ModStage, True, True, True);
  ModStage := '';
  ModPreparedPath := '';
end;

procedure InspectSelectedMod;
begin
  ClearPreparedMod;
  ModPasswordPage.Values[0] := '';
  ModInfo := ReadModZip(ModZipPath);
  if not ModInfo.Encrypted then
    ExtractSelectedMod('');
end;

function NextButtonClick(PageID: Integer): Boolean;
var
  MessageText, Password: String;
begin
  Result := True;
  if WizardSilent then
    Exit;
  try
    if PageID = wpSelectTasks then
    begin
      ModZipPath := '';
      ClearPreparedMod;
      if WizardIsTaskSelected('mods\chilean') then
      begin
        DownloadChileanMod;
        InspectSelectedMod;
      end;
    end
    else if PageID = ModFilePage.ID then
    begin
      ModZipPath := ExpandConstant('{tmp}\personal-mod.zip');
      if not FileCopy(ModFilePage.Values[0], ModZipPath, False) then
        RaiseException(CustomMessage('ModCopyFailed'));
      InspectSelectedMod;
    end
    else if PageID = ModPasswordPage.ID then
    begin
      Password := ModPasswordPage.Values[0];
      ModPasswordPage.Values[0] := '';
      if Password = '' then
        RaiseException(CustomMessage('ModPasswordRequired'));
      ExtractSelectedMod(Password);
    end;
  except
    MessageText := GetExceptionMessage;
    if (PageID = ModPasswordPage.ID) and
      (MessageText <> CustomMessage('ModPasswordRequired')) and
      (MessageText <> CustomMessage('ModNoSpace')) then
      MessageText := CustomMessage('ModPasswordFailed');
    MsgBox(MessageText, mbError, MB_OK);
    Result := False;
  end;
end;

function HasPreparedMod: Boolean;
begin
  Result := not WizardSilent and WizardIsTaskSelected('mods') and
    (ModPreparedPath <> '');
end;

function PreparedModPath(Param: String): String;
begin
  Result := ModPreparedPath;
  if Result = '' then
    Result := ExpandConstant('{tmp}\mod-not-selected');
end;

function IncomingModPath(Param: String): String;
begin
  Result := ModIncoming;
  if Result = '' then
    Result := ExpandConstant('{tmp}\mod-not-selected');
end;

procedure InstallingModFiles;
begin
  WizardForm.StatusLabel.Caption := CustomMessage('ModInstalling');
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Base, Token: String;
  FreeBytes, TotalBytes: Int64;
begin
  Result := '';
  if WizardSilent or not WizardIsTaskSelected('mods') then
    Exit;
  if not HasPreparedMod then
  begin
    Result := CustomMessage('ModInvalid');
    Exit;
  end;
  if GetSpaceOnDisk64(ExpandConstant('{app}'), FreeBytes, TotalBytes) and
    (FreeBytes < {#AppImageBytes} + ModInfo.ExpandedBytes) then
  begin
    Result := CustomMessage('ModNoSpace');
    Exit;
  end;
  Base := ExpandConstant('{app}\app\');
  Token := ExtractFileName(ExpandConstant('{tmp}'));
  if DirExists(Base + 'mod-incoming-' + Token) or
    FileExists(Base + 'mod-incoming-' + Token) or
    DirExists(Base + 'mod-backup-' + Token) or
    FileExists(Base + 'mod-backup-' + Token) then
  begin
    Result := CustomMessage('ModInstallFailed');
    Exit;
  end;
  ModIncoming := Base + 'mod-incoming-' + Token;
  ModBackup := Base + 'mod-backup-' + Token;
end;

procedure ActivatePreparedMod;
var
  Active: String;
  BackedUp: Boolean;
begin
  if not HasPreparedMod then
    Exit;
  Active := ExpandConstant('{app}\app\mod');
  if FileExists(ModIncoming + '\mod.xml') then
  begin
    BackedUp := DirExists(Active);
    if not BackedUp or RenameFile(Active, ModBackup) then
    begin
      if RenameFile(ModIncoming, Active) then
      begin
        ModIncoming := '';
        if BackedUp then
          Log('Previous MOD preserved in ' + ModBackup);
        Exit;
      end;
      if BackedUp and not RenameFile(ModBackup, Active) then
        SuppressibleMsgBox(FmtMessage(CustomMessage('ModBackupRetained'), [ModBackup]),
          mbError, MB_OK, IDOK);
    end;
  end;
  SuppressibleMsgBox(CustomMessage('ModInstallFailed'), mbError, MB_OK, IDOK);
end;

procedure DeinitializeSetup;
begin
  if ModIncoming <> '' then
    DelTree(ModIncoming, True, True, True);
end;

function EstimatedInstallSize: String;
var
  Bytes: Int64;
begin
  Bytes := {#AppImageBytes};
  if HasPreparedMod then
    Bytes := Bytes + ModInfo.ExpandedBytes;
  Result := IntToStr((Bytes + 1048575) div 1048576) + ' MiB';
end;

function UpdateReadyMemo(Space, NewLine, MemoUserInfoInfo, MemoDirInfo,
  MemoTypeInfo, MemoComponentsInfo, MemoGroupInfo, MemoTasksInfo: String): String;
begin
  Result := CustomMessage('InstallSize') + NewLine + Space +
    EstimatedInstallSize + NewLine + NewLine;
  if MemoUserInfoInfo <> '' then Result := Result + MemoUserInfoInfo + NewLine + NewLine;
  if MemoDirInfo <> '' then Result := Result + MemoDirInfo + NewLine + NewLine;
  if MemoTypeInfo <> '' then Result := Result + MemoTypeInfo + NewLine + NewLine;
  if MemoComponentsInfo <> '' then Result := Result + MemoComponentsInfo + NewLine + NewLine;
  if MemoGroupInfo <> '' then Result := Result + MemoGroupInfo + NewLine + NewLine;
  if MemoTasksInfo <> '' then Result := Result + MemoTasksInfo;
end;
